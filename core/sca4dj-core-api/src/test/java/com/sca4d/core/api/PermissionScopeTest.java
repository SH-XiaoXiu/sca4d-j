package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
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
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.permission.ResourceScope;
import com.sca4d.core.api.permission.ResourceScopeRequirement;
import com.sca4d.core.api.permission.SessionOperationPermissionPolicy;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
class PermissionScopeTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:20:00Z");

    @Test
    @DisplayName("Stores Resource Scopes By Complete Permission")
    void storesResourceScopesByCompletePermission() {
        Call call = Call.system("scope-test", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        Resource devices = new DeviceResource("collection");
        Permission read = new Permission(devices, Action.READ);
        Permission update = new Permission(devices, Action.UPDATE);
        DeviceScope readScope = new DeviceScope("read");
        DeviceScope updateScope = new DeviceScope("update");

        call.recordPermissionDecision(read, PermissionDecision.allow(readScope));
        call.recordPermissionDecision(update, PermissionDecision.allow(updateScope));
        CallExecution execution = execution(call);
        ResourceScopeRequirement.register(execution, read);
        ResourceScopeRequirement.register(execution, update);

        assertSame(readScope, ResourceScopeRequirement.consume(execution, read, DeviceScope.class).orElseThrow());
        assertSame(updateScope, ResourceScopeRequirement.consume(execution, update, DeviceScope.class).orElseThrow());
        assertEquals("read", ResourceScopeRequirement.consume(execution, read, DeviceScope.class).orElseThrow().name);
        assertThrows(
                IllegalStateException.class,
                () -> call.recordPermissionDecision(read, PermissionDecision.allow(readScope)));
    }

    @Test
    @DisplayName("Rejects Missing Denied And Mismatched Resource Scopes")
    void rejectsMissingDeniedAndMismatchedResourceScopes() {
        Call call = Call.system("scope-test", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        Permission devices = new Permission(new DeviceResource("collection"), Action.READ);
        Permission accounts = new Permission(new AccountResource("collection"), Action.READ);
        CallExecution execution = execution(call);

        assertThrows(IllegalStateException.class, () -> ResourceScopeRequirement.register(execution, devices));

        call.recordPermissionDecision(devices, PermissionDecision.deny("设备读取被拒绝"));
        assertThrows(IllegalStateException.class, () -> ResourceScopeRequirement.register(execution, devices));
        assertThrows(
                IllegalStateException.class,
                () -> call.recordPermissionDecision(accounts, PermissionDecision.allow(new DeviceScope("read"))));

        Call wrongType = Call.system("wrong-type", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        wrongType.recordPermissionDecision(accounts, PermissionDecision.allow(new AccountScope()));
        CallExecution wrongTypeExecution = execution(wrongType);
        ResourceScopeRequirement.register(wrongTypeExecution, accounts);
        assertThrows(
                IllegalStateException.class,
                () -> ResourceScopeRequirement.consume(wrongTypeExecution, accounts, DeviceScope.class));
    }

    @Test
    @DisplayName("Validates Permission Decision Scope Values")
    void validatesPermissionDecisionScopeValues() {
        PermissionDecision decision = PermissionDecision.allow(new DeviceScope("read"));

        assertTrue(decision.isAllowed());
        assertTrue(decision.reason().isEmpty());
        assertTrue(decision.scope().isPresent());
        assertEquals("read", decision.scopeAs(DeviceScope.class).name);
        assertThrows(NullPointerException.class, () -> PermissionDecision.allow(() -> null));

        PermissionDecision denied = PermissionDecision.deny("没有权限");
        assertFalse(denied.isAllowed());
        assertTrue(denied.scope().isEmpty());
        assertThrows(IllegalStateException.class, () -> denied.scopeAs(DeviceScope.class));
    }

    @Test
    @DisplayName("Uses Session Snapshot Only For Operation Policy")
    void usesSessionSnapshotOnlyForOperationPolicy() {
        Permission allowed = new Permission(new Operation("device:read"), Action.INVOKE);
        Permission denied = new Permission(new Operation("device:update"), Action.INVOKE);
        Call call = userCall(Set.of((Operation) allowed.resource()));
        SessionOperationPermissionPolicy policy = new SessionOperationPermissionPolicy();

        assertTrue(policy.supports(allowed.resource()));
        assertFalse(policy.supports(new DeviceResource("collection")));
        assertTrue(policy.decide(call, allowed).isAllowed());
        assertFalse(policy.decide(call, denied).isAllowed());
        assertThrows(
                IllegalStateException.class,
                () -> policy.decide(
                        call,
                        new Permission(new DeviceResource("collection"), Action.READ)));
    }

    @Test
    @DisplayName("Lets Business Policy Produce Scope Without Exact Session Grant")
    void letsBusinessPolicyProduceScopeWithoutExactSessionGrant() {
        Permission collectionRead = new Permission(new DeviceResource("collection"), Action.READ);
        Call call = Call.system("business-policy", NOW, new SystemPrincipal(1, TreeCode.ofSegment("MAIN")));
        PermissionPolicy policy = new DevicePermissionPolicy();

        PermissionDecision decision = new PermissionAssessment(List.of(policy))
                .assess(call, collectionRead);

        assertTrue(decision.isAllowed());
        assertTrue(decision.scope().isPresent());
    }

    private static Call userCall(Set<Operation> operations) {
        long subjectId = 10L;
        long accountId = 20L;
        long principalId = 30L;
        Subject subject = new Subject(subjectId, SubjectType.HUMAN);
        Account account = new Account(accountId, subjectId, new RealmKey("test"));
        TestPrincipal principal = new TestPrincipal(principalId);
        Session session = new Session(
                new SessionId("permission-scope"),
                subject,
                account,
                List.of(principal),
                List.of(),
                List.of(
                        new Authentication(
                                accountId,
                                TreeCode.ofSlash("AUTHENTICATION/TEST"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW)),
                Map.of(principalId, operations),
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
        Call call = new Call("scope-test", NOW);
        call.authenticate(session);
        call.selectPrincipal(principal);
        return call;
    }

    private static CallExecution execution(Call call) {
        try {
            Method method = TestCall.class.getDeclaredMethod("invoke");
            return new CallExecution(call, TestCall.class, method, new Object[0], method.getGenericReturnType());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private static class TestCall {

        private void invoke() {
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

    private static class DevicePermissionPolicy implements PermissionPolicy {

        @Override
        public boolean supports(Resource resource) {
            return resource.type().equals(TreeCode.ofSlash("TEST/DEVICE"));
        }

        @Override
        public PermissionDecision decide(Call call, Permission permission) {
            return PermissionDecision.allow(new DeviceScope("read"));
        }
    }

    private static class DeviceResource implements Resource {

        private final String id;

        private DeviceResource(String id) {
            this.id = id;
        }

        @Override
        public @NotNull TreeCode type() {
            return TreeCode.ofSlash("TEST/DEVICE");
        }

        @Override
        public @NotNull String id() {
            return id;
        }
    }

    private static class AccountResource implements Resource {

        private final String id;

        private AccountResource(String id) {
            this.id = id;
        }

        @Override
        public @NotNull TreeCode type() {
            return Account.RESOURCE_TYPE;
        }

        @Override
        public @NotNull String id() {
            return id;
        }
    }

    private static class DeviceScope implements ResourceScope {

        private final String name;

        private DeviceScope(String name) {
            this.name = name;
        }

        @Override
        public TreeCode resourceType() {
            return TreeCode.ofSlash("TEST/DEVICE");
        }
    }

    private static class AccountScope implements ResourceScope {

        @Override
        public TreeCode resourceType() {
            return Account.RESOURCE_TYPE;
        }
    }
}
