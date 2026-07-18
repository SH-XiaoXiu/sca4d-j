package com.sca4d.authentication.satoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.SessionSnapshot;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证 Sa-Token 登录、会话读取、替换和结束操作。 */
class SaTokenAuthenticationTest {

    @Test
    void shouldRejectPlatformPrincipalForTenantAccount() {
        Subject subject = new Subject(10L, "Alice");
        Account account = new Account(30L, 10L, AccountScopeType.TENANT, 100L);
        Principal principal = Principal.withDisplayName(20L, PrincipalType.PLATFORM, 10L, null, "Platform");

        assertThatThrownBy(() -> new SaTokenAuthentication(new ObjectMapper()).createSession(
                subject,
                account,
                List.of(principal),
                Map.of(20L, new SessionPermission(20L, Set.of(), Set.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Session Principal 与 Subject 不匹配");
    }

    @Test
    void shouldRejectPrincipalFromAnotherTenantForTenantAccount() {
        Subject subject = new Subject(10L, "Alice");
        Account account = new Account(30L, 10L, AccountScopeType.TENANT, 100L);
        Principal principal = Principal.withDisplayName(21L, PrincipalType.TENANT, 10L, 200L, "Tenant 200");

        assertThatThrownBy(() -> new SaTokenAuthentication(new ObjectMapper()).createSession(
                subject,
                account,
                List.of(principal),
                Map.of(21L, new SessionPermission(21L, Set.of(), Set.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Session Principal 与 Subject 不匹配");
    }

    @Test
    void shouldEndAllSubjectSessionsWithSingleAccountOperation() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            new SaTokenAuthentication(new ObjectMapper()).endSessions(10L);

            stp.verify(() -> StpUtil.logout(10L));
            stp.verifyNoMoreInteractions();
        }
    }

    @Test
    void shouldInvalidateSessionIdempotently() {
        Session source = TestSession.of(1L);
        SaTokenSession session = new SaTokenSession(
                "session-1",
                "Authorization",
                "token-1",
                source.getSubject(),
                source.getAccount(),
                source.getPrincipals(),
                source.getPermissions(),
                null,
                null,
                null,
                null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            session.invalidate();
            session.invalidate();

            stp.verify(() -> StpUtil.kickoutByTokenValue("token-1"));
        }
    }

    @Test
    void shouldListSafeSessionSnapshotsFromOneAccountSessionRead() {
        StpLogic logic = mock(StpLogic.class);
        SaTerminalInfo terminal = new SaTerminalInfo()
                .setTokenValue("secret-token")
                .setDeviceId("browser-1")
                .setCreateTime(1_700_000_000_000L)
                .setExtra(SaTokenAuthentication.SESSION_ID, "session-1")
                .setExtra(SaTokenAuthentication.TERMINAL_NAME, "Chrome");
        SaSession accountSession = new SaSession();
        accountSession.setTerminalList(List.of(terminal));
        when(logic.getSessionByLoginId(10L, false)).thenReturn(accountSession);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);

            var sessions = new SaTokenAuthentication(new ObjectMapper()).sessions(10L);

            assertThat(sessions).singleElement().satisfies(session -> {
                assertThat(session.getSessionId()).isEqualTo("session-1");
                assertThat(session.getAccountId()).isEqualTo(10L);
                assertThat(session.getDeviceId()).isEqualTo("browser-1");
                assertThat(session.getTerminalName()).isEqualTo("Chrome");
                assertThat(Arrays.stream(SessionSnapshot.class.getDeclaredFields())
                        .map(Field::getName))
                        .doesNotContain("token", "tokenValue");
            });
            verify(logic).getSessionByLoginId(10L, false);
        }
    }

    @Test
    void shouldResolveProjectSessionIdBeforeRevokingInternalToken() {
        StpLogic logic = mock(StpLogic.class);
        SaTerminalInfo terminal = new SaTerminalInfo()
                .setTokenValue("secret-token")
                .setExtra(SaTokenAuthentication.SESSION_ID, "session-1");
        SaSession accountSession = new SaSession();
        accountSession.setTerminalList(List.of(terminal));
        when(logic.getSessionByLoginId(10L, false)).thenReturn(accountSession);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);

            new SaTokenAuthentication(new ObjectMapper()).endSession(10L, "session-1");

            verify(logic).logoutByTokenValue("secret-token");
        }
    }

    @Test
    void shouldReadOnlineAccountIdsWithOneSessionSearch() {
        StpLogic logic = mock(StpLogic.class);
        when(logic.splicingKeySession("")).thenReturn("Authorization:login:session:");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);
            stp.when(() -> StpUtil.searchSessionId("", 0, -1, true)).thenReturn(List.of(
                    "Authorization:login:session:10",
                    "Authorization:login:session:20",
                    "Authorization:login:session:10"));

            List<Long> accountIds = new SaTokenAuthentication(new ObjectMapper()).onlineAccountIds();

            assertThat(accountIds).containsExactly(10L, 20L);
            stp.verify(() -> StpUtil.searchSessionId("", 0, -1, true));
        }
    }
}
