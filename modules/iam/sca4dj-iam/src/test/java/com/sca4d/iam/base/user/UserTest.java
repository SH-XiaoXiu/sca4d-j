package com.sca4d.iam.base.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Test
    @DisplayName("Passes Through All Three Platform Roles Without Retaining Their State")
    void passesThroughAllThreePlatformRoles() {
        Subject subject = subject();
        Account account = account(subject.subjectId());
        PlatformPrincipal principal = principal(31);

        User user = new User(subject, account, principal);

        assertThat(user).isInstanceOf(Principal.class);
        assertThat(user.subject()).isSameAs(subject);
        assertThat(user.account()).isSameAs(account);
        assertThat(user.subjectId()).isEqualTo(subject.subjectId());
        assertThat(user.type()).isEqualTo(subject.type());
        assertThat(user.accountId()).isEqualTo(account.accountId());
        assertThat(user.realmKey()).isEqualTo(account.realmKey());
        assertThat(user.principalId()).isEqualTo(principal.principalId());
        assertThat(user.userId()).isSameAs(user.principalId());
    }

    @Test
    @DisplayName("Rejects Account From Another Subject")
    void rejectsAccountFromAnotherSubject() {
        assertThatThrownBy(() -> new User(subject(), account(12L), principal(31L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于");
    }

    @Test
    @DisplayName("Creates User From A Single Platform Session")
    void createsUserFromSinglePlatformSession() {
        Subject subject = subject();
        Account account = account(subject.subjectId());
        PlatformPrincipal principal = principal(31);

        User user = User.from(session(subject, account, List.of(principal)));

        assertThat(user.subjectId()).isEqualTo(subject.subjectId());
        assertThat(user.accountId()).isEqualTo(account.accountId());
        assertThat(user.principalId()).isEqualTo(principal.principalId());
    }

    @Test
    @DisplayName("Rejects Non Platform Session Roles")
    void rejectsNonPlatformSessionRoles() {
        Subject subject = subject();
        Account account = account(subject.subjectId());
        PlatformPrincipal principal = principal(31);

        Account otherAccount = new Account(account.accountId(), subject.subjectId(), new RealmKey("other"));
        assertThatThrownBy(() -> User.from(session(subject, otherAccount, List.of(principal))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("platform realm");
        assertThatThrownBy(() -> User.from(session(subject, account, List.of(new OtherPrincipal()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PlatformPrincipal");
    }

    @Test
    @DisplayName("Rejects Zero Or Multiple Session Principals")
    void rejectsZeroOrMultipleSessionPrincipals() {
        Subject subject = subject();
        Account account = account(subject.subjectId());
        Session empty = mock(Session.class);
        when(empty.subject()).thenReturn(subject);
        when(empty.account()).thenReturn(account);
        when(empty.principals()).thenReturn(List.of());

        assertThatThrownBy(() -> User.from(empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("恰好包含一个");
        assertThatThrownBy(() -> User.from(session(subject, account, List.of(principal(31), principal(32)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("恰好包含一个");
    }

    private static Subject subject() {
        return new Subject(11, SubjectType.HUMAN);
    }

    private static Account account(Long subjectId) {
        return new Account(21, subjectId, PlatformIdentityService.PLATFORM_REALM);
    }

    private static PlatformPrincipal principal(long id) {
        return new PlatformPrincipal(id);
    }

    private static Session session(
            Subject subject,
            Account account,
            List<? extends Principal> principals) {
        Map<Long, List<Resource>> resources = new LinkedHashMap<>();
        for (Principal principal : principals) {
            resources.put(principal.principalId(), List.of());
        }
        return new Session(
                new SessionId("session-1"),
                subject,
                account,
                principals,
                List.of(),
                List.of(
                        new Authentication(
                                account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/PASSWORD"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                NOW)),
                resources,
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
    }

    private static final class OtherPrincipal implements Principal {

        @Override
        public long principalId() {
            return 99L;
        }
    }
}
