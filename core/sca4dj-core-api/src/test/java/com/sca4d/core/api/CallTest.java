package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("ClassCanBeRecord")
class CallTest {

    @Test
    @DisplayName("异步子调用复制身份并隔离输入和权限结论")
    void forksIndependentCalls() {
        Identity identity = new Identity(10, 20, 30);
        Call parent = new Call("parent", STARTED_AT);
        parent.authenticate(session(identity, Set.of()));
        parent.selectPrincipal(identity.principal);
        Permission read = new Permission(new Operation(TreeCode.of("TEST/READ")), Action.INVOKE);
        parent.recordPermissionDecision(read, PermissionDecision.allow());
        DataKey<String> task = DataKey.of("task", String.class);
        Call child = parent.fork("child", STARTED_AT, DataValues.builder().put(task, "work").build(), read.resource());
        assertSame(parent.session().orElseThrow(), child.session().orElseThrow());
        assertEquals("parent", child.parentCallId().orElseThrow());
        assertEquals("work", child.input(task).orElseThrow());
        assertTrue(parent.input(task).isEmpty());
        assertTrue(child.permissionDecision(read).isEmpty());
        child.recordPermissionDecision(read, PermissionDecision.deny("任务拒绝"));
        assertTrue(parent.permissionDecision(read).orElseThrow().isAllowed());
        Call automatic = parent.fork();
        assertFalse(automatic.callId().equals(parent.callId()));
        assertEquals("parent", automatic.parentCallId().orElseThrow());
        assertTrue(automatic.inputs().asMap().isEmpty());
        assertTrue(automatic.targetResource().isEmpty());
        assertTrue(automatic.permissionDecision(read).isEmpty());
        assertEquals("work", parent.fork(DataValues.builder().put(task, "work").build()).input(task).orElseThrow());
        assertSame(
                read.resource(),
                parent.fork(DataValues.builder().build(), read.resource()).targetResource().orElseThrow());
        assertEquals(STARTED_AT, parent.fork("custom-child", STARTED_AT).startedAt());
        assertThrows(
                IllegalArgumentException.class,
                () -> parent.fork("parent", STARTED_AT, DataValues.builder().build(), null));
    }

    private static final Instant STARTED_AT = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    @DisplayName("Exposes Only Built In Subject Types")
    void exposesOnlyBuiltInSubjectTypes() {
        assertArrayEquals(
                new SubjectType[] {
                        SubjectType.HUMAN,
                        SubjectType.DEVICE,
                        SubjectType.APPLICATION,
                        SubjectType.SYSTEM
                },
                SubjectType.values());
        assertSame(SubjectType.SYSTEM, Subject.SYSTEM.type());
    }

    @Test
    @DisplayName("Binds Session Identity Principal And Resources Once")
    void bindsSessionIdentityPrincipalAndResourcesOnce() {
        Identity identity = new Identity(10, 20, 30);
        Operation operation = new Operation("platform:account:read");
        Session session = session(identity, Set.of(operation));
        Call call = new Call("call-1", STARTED_AT);

        call.authenticate(session);
        call.selectPrincipal(identity.principal);

        assertSame(session, call.session().orElseThrow());
        assertSame(session.subject(), call.subject().orElseThrow());
        assertSame(session.account(), call.account().orElseThrow());
        assertSame(session.principals().getFirst(), call.principal().orElseThrow());
        assertTrue(call.grants(operation));
        assertTrue(call.grants("platform:account:read"));
        assertFalse(call.grants(new Operation("platform:account:update")));
        assertFalse(call.grants("platform:account:update"));
        call.selectPrincipal(identity.principal);
        assertThrows(IllegalStateException.class, () -> call.authenticate(session));
    }

    @Test
    @DisplayName("Rejects Principal Outside Session And Principal Switching")
    void rejectsPrincipalOutsideSessionAndPrincipalSwitching() {
        Identity identity = new Identity(10, 20, 30);
        Identity another = new Identity(11, 21, 31);
        Call call = new Call("call-2", STARTED_AT);
        call.authenticate(session(identity, Set.of()));

        assertThrows(IllegalArgumentException.class, () -> call.selectPrincipal(another.principal));
        call.selectPrincipal(identity.principal);
        assertThrows(IllegalStateException.class, () -> call.selectPrincipal(new TestPrincipal(99)));
    }

    @Test
    @DisplayName("Binds A Runtime Principal Only After Compact Session Validation")
    @SuppressWarnings("ExtractMethodRecommender")
    // Session 准备、选择和拒绝结果共同表达一个公开行为。
    void bindsARuntimePrincipalOnlyAfterCompactSessionValidation() {
        Subject subject = new Subject(10, SubjectType.HUMAN);
        Account account = new Account(20, subject.subjectId(), new RealmKey("test"));
        Session session = new Session(
                new SessionId("session-compact"),
                subject,
                account,
                List.of(),
                List.of(new PrincipalIdSet<>(TestPrincipal.class, List.of(30L))),
                List.of(
                        new Authentication(
                                account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/TEST"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                STARTED_AT)),
                Map.of(),
                Map.of(),
                STARTED_AT,
                STARTED_AT.plusSeconds(3600));
        TestPrincipal selected = new TestPrincipal(30);
        Call call = new Call("call-compact", STARTED_AT);
        call.authenticate(session);

        call.selectPrincipal(selected);

        assertSame(selected, call.principal().orElseThrow());
        assertTrue(session.principals().isEmpty());
        assertFalse(call.grants(new Operation("project:read")));
        Call wrongType = new Call("call-wrong-type", STARTED_AT);
        wrongType.authenticate(session);
        assertThrows(IllegalArgumentException.class, () -> wrongType.selectPrincipal(new OtherPrincipal(30)));
    }

    @Test
    @DisplayName("Creates System Call With Fixed Subject And Principal")
    void createsSystemCallWithFixedSubjectAndPrincipal() {
        SystemPrincipal principal = new SystemPrincipal(2, TreeCode.ofSegment("IOT"));
        Call call = Call.system("system-call", STARTED_AT, principal);

        assertSame(Subject.SYSTEM, call.subject().orElseThrow());
        assertSame(principal, call.principal().orElseThrow());
        assertEquals(STARTED_AT, call.startedAt());
        assertTrue(call.session().isEmpty());
        assertTrue(call.account().isEmpty());
        assertTrue(call.isAuthenticationAssessed());
        assertTrue(call.isPrincipalAssessed());
        assertFalse(call.grants(new Operation("system:operation")));
    }

    @Test
    @DisplayName("Call 直接封装同步和异步纯函数并统一转发到生命周期执行器")
    void invokesFunctionsThroughBoundLifecycleInvoker() throws Exception {
        CallInvoker invoker = new CallInvoker(List.of());
        Call call = Call.system(
                invoker,
                "function-call",
                STARTED_AT,
                new SystemPrincipal(1, TreeCode.ofSegment("TEST")));

        assertEquals(
                "done",
                call.invoke(
                        TreeCode.ofColon("JOB:REFRESH"),
                        new Object[] {42L},
                        String.class,
                        () -> "done"));
        assertEquals(
                "async",
                call.invokeAsync(
                        "JOB:REFRESH",
                        new Object[] {42L},
                        String.class,
                        () -> CompletableFuture.completedFuture("async"))
                        .toCompletableFuture()
                        .join());
        assertThrows(
                IllegalArgumentException.class,
                () -> call.invoke("JOB/REFRESH", new Object[0], String.class, () -> "invalid"));
    }

    private static Session session(Identity identity, Set<Operation> operations) {
        return new Session(
                new SessionId("session-" + identity.account.accountId()),
                identity.subject,
                identity.account,
                List.of(identity.principal),
                List.of(),
                List.of(
                        new Authentication(
                                identity.account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/TEST"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                STARTED_AT)),
                Map.of(identity.principal.principalId(), operations),
                Map.of(),
                STARTED_AT,
                STARTED_AT.plusSeconds(3600));
    }

    private static class Identity {
        private final Subject subject;

        private final Account account;

        private final TestPrincipal principal;

        private Identity(long subjectId, long accountId, long principalId) {
            this.subject = new Subject(subjectId, SubjectType.HUMAN);
            this.account = new Account(accountId, subjectId, new RealmKey("test"));
            this.principal = new TestPrincipal(principalId);
        }
    }

    private static class TestPrincipal implements Principal {
        private final long id;

        private TestPrincipal(long id) {
            this.id = id;
        }

        @Override
        public long principalId() {
            return id;
        }
    }

    private static class OtherPrincipal implements Principal {
        private final long id;

        private OtherPrincipal(long id) {
            this.id = id;
        }

        @Override
        public long principalId() {
            return id;
        }
    }
}
