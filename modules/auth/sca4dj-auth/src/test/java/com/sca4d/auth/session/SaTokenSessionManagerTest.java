package com.sca4d.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.auth.testing.SessionTestFixture;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.authentication.exception.SessionIdentityUnavailableException;
import com.sca4d.core.api.permission.OperationCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 验证 Sa-Token Session 的恢复、按代次与 Operation 目录刷新、注销及建立期间的失效检查。 */
@ExtendWith(MockitoExtension.class)
class SaTokenSessionManagerTest {

    @Mock
    private SessionCodec codec;

    @Mock
    private StpLogic stpLogic;

    @Mock
    private SessionRefreshService refresh;

    @Mock
    private RedisSessionGeneration generations;

    @Mock
    private RedisSessionTokenIndex tokenIndex;

    private final OperationCatalog operations = new OperationCatalog(List.of());

    private SaTokenSessionManager sessions;

    @BeforeEach
    void setUp() {
        sessions = new SaTokenSessionManager(
                codec,
                refresh,
                generations,
                operations,
                tokenIndex,
                stpLogic,
                Clock.fixed(SessionTestFixture.NOW.plusSeconds(60), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Restores One Valid Session From The Current Token Session")
    void restoresOneValidSessionFromTheCurrentTokenSession() {
        Session session = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = tokenSession("token-session", "wire");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(codec.decode(eq("wire"))).thenReturn(session);
        when(stpLogic.getLoginIdAsLong()).thenReturn(session.account().accountId());
        when(generations.current(session.account().accountId())).thenReturn(0L);

        assertThat(sessions.current()).containsSame(session);

        verify(codec).decode("wire");
        verify(refresh, never()).refresh(session);
        verify(stpLogic, never()).logout();
    }

    @Test
    @DisplayName("Restores By An Explicit Token Without Reading The Current Request")
    void restoresByAnExplicitTokenWithoutReadingTheCurrentRequest() {
        Session session = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = tokenSession("explicit", "wire");
        when(stpLogic.getLoginIdByToken("token-value")).thenReturn(session.account().accountId());
        when(stpLogic.getTokenSessionByToken("token-value", false)).thenReturn(tokenSession);
        when(codec.decode(eq("wire"))).thenReturn(session);
        when(generations.current(session.account().accountId())).thenReturn(0L);

        assertThat(sessions.current("token-value")).containsSame(session);

        verify(stpLogic, never()).getTokenValue();
        verify(stpLogic, never()).logoutByTokenValue("token-value");
    }

    @Test
    @DisplayName("Clears Malformed Expired And Login ID Mismatched Sessions")
    void clearsMalformedExpiredAndLoginIdMismatchedSessions() {
        SaSession malformed = tokenSession("malformed", "broken");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(malformed);
        when(codec.decode(eq("broken"))).thenThrow(new IllegalStateException("bad wire"));
        assertThat(sessions.current()).isEmpty();
        verify(stpLogic).logout();

        Mockito.reset(codec, stpLogic);
        Session expired = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        sessions = new SaTokenSessionManager(
                codec,
                refresh,
                generations,
                operations,
                tokenIndex,
                stpLogic,
                Clock.fixed(SessionTestFixture.NOW.plusSeconds(7200), ZoneOffset.UTC));
        SaSession expiredToken = tokenSession("expired", "expired-wire");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(expiredToken);
        when(codec.decode(eq("expired-wire"))).thenReturn(expired);
        when(stpLogic.getLoginIdAsLong()).thenReturn(expired.account().accountId());
        assertThat(sessions.current()).isEmpty();
        verify(stpLogic).logout();

        Mockito.reset(codec, stpLogic);
        sessions = new SaTokenSessionManager(
                codec,
                refresh,
                generations,
                operations,
                tokenIndex,
                stpLogic,
                Clock.fixed(SessionTestFixture.NOW.plusSeconds(60), ZoneOffset.UTC));
        Session mismatched = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession mismatchedToken = tokenSession("mismatch", "valid-wire");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(mismatchedToken);
        when(codec.decode(eq("valid-wire"))).thenReturn(mismatched);
        when(stpLogic.getLoginIdAsLong()).thenReturn(999L);
        assertThat(sessions.current()).isEmpty();
        verify(stpLogic).logout();
    }

    @Test
    @DisplayName("Clears Known Invalid Tokens But Leaves Anonymous Requests Untouched")
    void clearsKnownInvalidTokensButLeavesAnonymousRequestsUntouched() {
        when(stpLogic.isLogin()).thenReturn(false);
        when(stpLogic.getTokenValue()).thenReturn(null);
        assertThat(sessions.current()).isEmpty();
        verify(stpLogic, never()).logout();

        Mockito.reset(stpLogic);
        when(stpLogic.isLogin()).thenReturn(false);
        when(stpLogic.getTokenValue()).thenReturn("invalid-token");
        assertThat(sessions.current()).isEmpty();
        verify(stpLogic).logout();

        Mockito.reset(stpLogic);
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenThrow(
                new NotLoginException(
                        "token invalid",
                        "login",
                        NotLoginException.INVALID_TOKEN));
        assertThat(sessions.current()).isEmpty();
        verify(stpLogic).logout();
    }

    @Test
    @DisplayName("Propagates Backing Store Failures")
    void propagatesBackingStoreFailures() {
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(stpLogic.isLogin()).thenThrow(failure);

        assertThatThrownBy(sessions::current).isSameAs(failure);
        verify(stpLogic, never()).logout();
    }

    @Test
    @DisplayName("Refreshes One Time When The Account Generation Changes")
    void refreshesOneTimeWhenTheAccountGenerationChanges() {
        Session previous = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        Session refreshed = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = tokenSession("refresh", "wire");
        tokenSession.set(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE, "previous-catalog");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(stpLogic.getLoginIdAsLong()).thenReturn(previous.account().accountId());
        when(generations.current(previous.account().accountId())).thenReturn(1L);
        when(codec.decode(eq("wire"))).thenReturn(previous);
        when(refresh.refresh(eq(previous))).thenReturn(refreshed);
        when(codec.encode(refreshed)).thenReturn("refreshed-wire");
        when(codec.decode(eq("refreshed-wire"))).thenReturn(refreshed);

        assertThat(sessions.current()).containsSame(refreshed);
        assertThat(sessions.current()).containsSame(refreshed);

        verify(refresh, times(1)).refresh(previous);
        assertThat(tokenSession.get(SaTokenSessionManager.SESSION_ATTRIBUTE)).isEqualTo("refreshed-wire");
        assertThat(tokenSession.get(SaTokenSessionManager.INVALIDATION_GENERATION_ATTRIBUTE)).isEqualTo(1L);
        assertThat(tokenSession.get(SaTokenSessionManager.LOADED_GENERATION_ATTRIBUTE)).isEqualTo(1L);
        assertThat(tokenSession.get(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE))
                .isEqualTo(operations.fingerprint());
    }

    @Test
    @DisplayName("Refreshes A Legacy Session Without An Operation Catalog Fingerprint Once")
    void refreshesLegacySessionWithoutCatalogFingerprintOnce() {
        Session previous = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        Session refreshed = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = tokenSession("legacy-catalog", "wire");
        tokenSession.delete(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE);
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(stpLogic.getLoginIdAsLong()).thenReturn(previous.account().accountId());
        when(generations.current(previous.account().accountId())).thenReturn(0L);
        when(codec.decode(eq("wire"))).thenReturn(previous);
        when(refresh.refresh(eq(previous))).thenReturn(refreshed);
        when(codec.encode(refreshed)).thenReturn("refreshed-wire");
        when(codec.decode(eq("refreshed-wire"))).thenReturn(refreshed);

        assertThat(sessions.current()).containsSame(refreshed);
        assertThat(sessions.current()).containsSame(refreshed);

        verify(refresh).refresh(previous);
        assertThat(tokenSession.get(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE))
                .isEqualTo(operations.fingerprint());
    }

    @Test
    @DisplayName("Refreshes Once When The Operation Catalog Changes")
    void refreshesOnceWhenOperationCatalogChanges() {
        Session previous = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        Session refreshed = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = tokenSession("changed-catalog", "wire");
        tokenSession.set(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE, "previous-catalog");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(stpLogic.getLoginIdAsLong()).thenReturn(previous.account().accountId());
        when(generations.current(previous.account().accountId())).thenReturn(0L);
        when(codec.decode(eq("wire"))).thenReturn(previous);
        when(refresh.refresh(eq(previous))).thenReturn(refreshed);
        when(codec.encode(refreshed)).thenReturn("refreshed-wire");
        when(codec.decode(eq("refreshed-wire"))).thenReturn(refreshed);

        assertThat(sessions.current()).containsSame(refreshed);
        assertThat(sessions.current()).containsSame(refreshed);

        verify(refresh).refresh(previous);
        assertThat(tokenSession.get(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE))
                .isEqualTo(operations.fingerprint());
    }

    @Test
    @DisplayName("Keeps The Invalid Generation When Refresh Infrastructure Fails")
    void keepsTheInvalidGenerationWhenRefreshInfrastructureFails() {
        Session previous = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = tokenSession("retry", "wire");
        tokenSession.set(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE, "previous-catalog");
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(stpLogic.getLoginIdAsLong()).thenReturn(previous.account().accountId());
        when(generations.current(previous.account().accountId())).thenReturn(2L);
        when(codec.decode(eq("wire"))).thenReturn(previous);
        when(refresh.refresh(eq(previous))).thenThrow(failure);

        assertThatThrownBy(sessions::current).isSameAs(failure);

        assertThat(tokenSession.get(SaTokenSessionManager.INVALIDATION_GENERATION_ATTRIBUTE)).isEqualTo(2L);
        assertThat(tokenSession.get(SaTokenSessionManager.LOADED_GENERATION_ATTRIBUTE)).isEqualTo(0L);
        assertThat(tokenSession.get(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE))
                .isEqualTo("previous-catalog");
        verify(stpLogic, never()).logout();
    }

    @Test
    @DisplayName("Logs Out When The Session Identity Is Unavailable")
    void logsOutWhenTheSessionIdentityIsUnavailable() {
        Session previous = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = tokenSession("unavailable", "wire");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(stpLogic.getLoginIdAsLong()).thenReturn(previous.account().accountId());
        when(generations.current(previous.account().accountId())).thenReturn(3L);
        when(codec.decode(eq("wire"))).thenReturn(previous);
        when(refresh.refresh(eq(previous)))
                .thenThrow(new SessionIdentityUnavailableException(new IllegalStateException("disabled")));

        assertThat(sessions.current()).isEmpty();

        verify(stpLogic).logout();
        verify(tokenIndex).delete(previous.id());
        assertThat(tokenSession.get(SaTokenSessionManager.LOADED_GENERATION_ATTRIBUTE)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Terminates The Backing Session Only Once For Repeated Logout")
    void terminatesTheBackingSessionOnlyOnceForRepeatedLogout() {
        Session session = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        when(tokenIndex.find(session.id()))
                .thenReturn(Optional.of("token-value"))
                .thenReturn(Optional.empty());

        sessions.terminate(session.id(), SessionTerminationReason.USER_REQUESTED);
        sessions.terminate(session.id(), SessionTerminationReason.USER_REQUESTED);

        verify(stpLogic).logoutByTokenValue("token-value");
        verify(tokenIndex).delete(session.id());
    }

    @Test
    @DisplayName("Removes A New Backing Session When Generation Changes During Establishment")
    void removesANewBackingSessionWhenGenerationChangesDuringEstablishment() {
        Session session = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = new SaSession("new-session");
        when(generations.current(session.account().accountId())).thenReturn(0L, 1L);
        when(stpLogic.getTokenValue()).thenReturn("new-token");
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(codec.encode(session)).thenReturn("new-wire");

        assertThatThrownBy(() -> sessions.establish(session, Duration.ofHours(1), 0L))
                .isInstanceOf(SessionIdentityUnavailableException.class);

        verify(stpLogic).logout();
        verify(tokenIndex).delete(session.id());
    }

    @Test
    @DisplayName("Stores The Current Operation Catalog Fingerprint When Establishing A Session")
    void storesCatalogFingerprintWhenEstablishingSession() {
        Session session = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600));
        SaSession tokenSession = new SaSession("new-session");
        when(generations.current(session.account().accountId())).thenReturn(0L);
        when(stpLogic.getTokenValue()).thenReturn("new-token");
        when(stpLogic.getTokenSession()).thenReturn(tokenSession);
        when(codec.encode(session)).thenReturn("new-wire");

        assertThat(sessions.establish(session, Duration.ofHours(1), 0L)).isEqualTo("new-token");

        assertThat(tokenSession.get(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE))
                .isEqualTo(operations.fingerprint());
    }

    private SaSession tokenSession(String id, String wire) {
        return new SaSession(id)
                .set(SaTokenSessionManager.SESSION_ATTRIBUTE, wire)
                .set(SaTokenSessionManager.INVALIDATION_GENERATION_ATTRIBUTE, 0L)
                .set(SaTokenSessionManager.LOADED_GENERATION_ATTRIBUTE, 0L)
                .set(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE, operations.fingerprint());
    }
}
