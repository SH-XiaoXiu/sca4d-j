package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.LongIdSet;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
class SessionContractTest {

    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");

    @Test
    @DisplayName("Keeps concrete identity and resources without inferring principal subjects")
    void keepsConcreteIdentityAndResourceInstancesWithoutPrincipalSubjectInference() {
        Subject subject = new Subject(10, SubjectType.HUMAN);
        Account account = new Account(20, subject.subjectId(), new RealmKey("test"));
        TestPrincipal principal = new TestPrincipal(30);
        TestResource resource = new TestResource("operation", "platform:account:read");
        Session session = session(subject, account, principal, List.of(resource));

        assertSame(subject, session.subject());
        assertSame(account, session.account());
        assertSame(principal, session.principalById(principal.principalId()));
        assertSame(resource, session.resources(principal.principalId()).getFirst());
        assertTrue(session.grants(principal.principalId(), new TestResource("operation", "platform:account:read")));
    }

    @Test
    @DisplayName("Rejects Duplicate Resources And Principals")
    void rejectsDuplicateResourcesAndPrincipals() {
        Subject subject = new Subject(10, SubjectType.HUMAN);
        Account account = new Account(20, subject.subjectId(), new RealmKey("test"));
        TestPrincipal principal = new TestPrincipal(30);

        assertThrows(
                IllegalArgumentException.class,
                () -> session(
                        subject,
                        account,
                        principal,
                        List.of(new TestResource("operation", "read"), new TestResource("operation", "read"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Session(
                        new SessionId("session-1"),
                        subject,
                        account,
                        List.of(principal, new TestPrincipal(principal.principalId())),
                        List.of(),
                        authentications(account),
                        Map.of(principal.principalId(), List.of()),
                        Map.of(),
                        NOW,
                        NOW.plusSeconds(3600)));
    }

    @Test
    @DisplayName("Validates Runtime Principals By Exact Type And Compact ID")
    void validatesRuntimePrincipalsByExactTypeAndCompactId() {
        Subject subject = new Subject(10, SubjectType.HUMAN);
        Account account = new Account(20, subject.subjectId(), new RealmKey("test"));
        TestPrincipal complete = new TestPrincipal(30);
        Session session = new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(complete),
                List.of(new PrincipalIdSet<>(TestPrincipal.class, List.of(31L, 32L))),
                authentications(account),
                Map.of(complete.principalId(), List.of()),
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
        TestPrincipal runtime = new TestPrincipal(31);

        assertSame(complete, session.principal(new TestPrincipal(complete.principalId())));
        assertSame(runtime, session.principal(runtime));
        assertTrue(session.containsPrincipal(TestPrincipal.class, runtime.principalId()));
        assertThrows(IllegalArgumentException.class, () -> session.principal(new OtherPrincipal(31)));
        assertThrows(IllegalArgumentException.class, () -> session.principal(new TestPrincipal(99)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrincipalIdSet<>(Principal.class, List.of(31L)));
        assertTrue(session.principal(runtime.principalId()).isEmpty());
        assertTrue(session.resources(runtime.principalId()).isEmpty());
    }

    @Test
    @DisplayName("Matches Compact Numeric Resources Without Changing Text Resources")
    void matchesCompactNumericResourcesWithoutChangingTextResources() {
        Subject subject = new Subject(10, SubjectType.HUMAN);
        Account account = new Account(20, subject.subjectId(), new RealmKey("test"));
        TestPrincipal principal = new TestPrincipal(30);
        Session session = new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(principal),
                List.of(),
                authentications(account),
                Map.of(principal.principalId(), List.of(new TestResource("operation", "project:read"))),
                Map.of(
                        principal.principalId(),
                        List.of(
                                new ResourceIdSet(
                                        TreeCode.ofSegment("PROJECT"),
                                        List.of(1_927_273_629L, 1_927_273_630L)))),
                NOW,
                NOW.plusSeconds(3600));

        assertTrue(session.grants(principal.principalId(), new TestResource("project", "1927273629")));
        assertTrue(session.grants(principal.principalId(), new TestResource("operation", "project:read")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LongIdSet(List.of(3L, 2L, 3L)));
        Assertions.assertFalse(
                session.grants(principal.principalId(), new TestResource("project", "01927273629")));
        Assertions.assertFalse(
                session.grants(principal.principalId(), new TestResource("project", "project:read")));
        Assertions.assertFalse(
                session.grants(principal.principalId(), new TestResource("other", "1927273629")));
    }

    @Test
    @DisplayName("Requires Session Identity Account To Belong To Subject")
    void requiresSessionIdentityAccountToBelongToSubject() {
        Subject subject = new Subject(10, SubjectType.HUMAN);
        Account account = new Account(20, subject.subjectId(), new RealmKey("test"));
        SessionIdentity identity = new SessionIdentity(subject, account);

        assertSame(subject, identity.subject());
        assertSame(account, identity.account());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionIdentity(subject, new Account(21, 99, new RealmKey("test"))));
    }

    private static Session session(
            Subject subject,
            Account account,
            Principal principal,
            List<? extends Resource> resources) {
        return new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(principal),
                List.of(),
                authentications(account),
                Map.of(principal.principalId(), resources),
                Map.of(),
                NOW,
                NOW.plusSeconds(3600));
    }

    private static List<Authentication> authentications(Account account) {
        return List.of(
                new Authentication(
                        account.accountId(),
                        TreeCode.ofSlash("AUTHENTICATION/TEST"),
                        Set.of(AuthenticationFactorKind.KNOWLEDGE),
                        NOW));
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

    private static class TestResource implements Resource {
        private final String id;

        private final TreeCode type;

        private TestResource(String type, String id) {
            this.type = TreeCode.ofSegment(type);
            this.id = id;
        }

        @Override
        public @NotNull TreeCode type() {
            return type;
        }

        @Override
        public @NotNull String id() {
            return id;
        }
    }
}
