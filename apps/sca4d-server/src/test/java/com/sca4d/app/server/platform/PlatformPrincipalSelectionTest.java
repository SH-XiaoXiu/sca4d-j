package com.sca4d.app.server.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
class PlatformPrincipalSelectionTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    private final PlatformPrincipalSelection selection = new PlatformPrincipalSelection();

    @Test
    @DisplayName("Supports selection before controller argument binding")
    void supportsSelectionBeforeControllerArgumentBinding() {
        assertThat(selection.supportsSelectionBeforeArgumentBinding()).isTrue();
    }

    @Test
    @DisplayName("Selects the only platform principal from a platform account session")
    void selectsTheOnlyPlatformPrincipalFromAPlatformAccountSession() {
        PlatformPrincipal principal = new PlatformPrincipal(31);
        Session session = session(List.of(principal, new OtherPrincipal(32)));

        assertThat(selection.select(session, session.subject(), session.account(), Map.of())).isSameAs(principal);
    }

    @Test
    @DisplayName("Rejects Missing And Selects Among Multiple Platform Principals")
    void rejectsMissingAndSelectsAmongMultiplePlatformPrincipals() {
        Session missing = session(List.of(new OtherPrincipal(32)));
        Session duplicate = session(
                List.of(
                        new PlatformPrincipal(31),
                        new PlatformPrincipal(32)));

        assertThatThrownBy(() -> selection.select(missing, missing.subject(), missing.account(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须包含 PlatformPrincipal");
        Principal selected = selection.select(
                duplicate,
                duplicate.subject(),
                duplicate.account(),
                Map.of("principalId", "32"));
        assertThat(selected).isNotNull();
        assertThat(selected.principalId()).isEqualTo(32);
    }

    @Test
    @DisplayName("Does Not Claim Anonymous Or Non Platform Accounts")
    void doesNotClaimAnonymousOrNonPlatformAccounts() {
        Session session = session(List.of(new PlatformPrincipal(31)));

        assertThat(selection.select(null, null, null, Map.of())).isNull();
        Account other = new Account(99, session.subject().subjectId(), new RealmKey("other"));
        assertThat(selection.select(session, session.subject(), other, Map.of())).isNull();
    }

    private static Session session(List<? extends Principal> principals) {
        Subject subject = new Subject(11, SubjectType.HUMAN);
        Account account = new Account(21, subject.subjectId(), PlatformIdentityService.PLATFORM_REALM);
        LinkedHashMap<Long, List<com.sca4d.core.api.resource.Resource>> resources = new LinkedHashMap<>();
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
