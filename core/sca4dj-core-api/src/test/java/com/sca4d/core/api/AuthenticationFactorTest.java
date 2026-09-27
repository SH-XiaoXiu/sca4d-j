package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
class AuthenticationFactorTest {

    private static final Instant NOW = Instant.parse("2026-07-20T00:10:00Z");

    @Test
    @DisplayName("Keeps Distinct MFA Factors In Assessed Session")
    void keepsDistinctMfaFactorsInAssessedSession() {
        TestIdentity identity = new TestIdentity();
        Call call = identity.call(
                List.of(
                        new Authentication(
                                identity.account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/PASSWORD"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW.minusSeconds(60)),
                        new Authentication(
                                identity.account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/PASSKEY"),
                                Set.of(AuthenticationFactorKind.POSSESSION),
                                NOW.minusSeconds(30))));
        EnumSet<AuthenticationFactorKind> kinds = EnumSet.noneOf(AuthenticationFactorKind.class);
        call.session()
                .orElseThrow()
                .authentications()
                .forEach(authentication -> kinds.addAll(authentication.factorKinds()));

        assertEquals(Set.of(AuthenticationFactorKind.KNOWLEDGE, AuthenticationFactorKind.POSSESSION), kinds);
    }

    @Test
    @DisplayName("Requires Exactly One Permission Policy To Claim Resource")
    void requiresExactlyOnePermissionPolicyToClaimResource() {
        TestIdentity identity = new TestIdentity();
        Call call = identity.call(
                List.of(
                        new Authentication(
                                identity.account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/PASSWORD"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW.minusSeconds(10))));
        call.selectPrincipal(identity.principal);
        Permission permission = new Permission(new DeviceResource("device-1"), new Action("rename"));
        PermissionPolicy policy = new DevicePolicy();

        assertTrue(
                new PermissionAssessment(List.of(policy))
                        .assess(call, permission)
                        .isAllowed());
        assertThrows(
                IllegalStateException.class,
                () -> new PermissionAssessment(List.of()).assess(call, permission));
        assertThrows(
                IllegalStateException.class,
                () -> new PermissionAssessment(List.of(policy, new DevicePolicy()))
                        .assess(call, permission));
    }

    private static final class DeviceResource implements Resource {

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

    private static final class DevicePolicy implements PermissionPolicy {

        @Override
        public boolean supports(Resource resource) {
            return resource.type().equals(TreeCode.ofSlash("TEST/DEVICE"));
        }

        @Override
        public PermissionDecision decide(Call call, Permission permission) {
            return PermissionDecision.allow();
        }
    }

    private static final class TestIdentity {

        private final Subject subject = new Subject(10, SubjectType.HUMAN);

        private final Account account = new Account(20, subject.subjectId(), new RealmKey("test"));

        private final Principal principal = new TestPrincipal();

        private Call call(List<Authentication> authentications) {
            Session session = new Session(
                    new SessionId("session-1"),
                    subject,
                    account,
                    List.of(principal),
                    List.of(),
                    authentications,
                    Map.of(principal.principalId(), Set.of()),
                    Map.of(),
                    NOW.minusSeconds(600),
                    NOW.plusSeconds(600));
            Call call = new Call("call-1", NOW);
            call.authenticate(session);
            return call;
        }
    }

    private static final class TestPrincipal implements Principal {

        @Override
        public long principalId() {
            return 30L;
        }

    }
}
