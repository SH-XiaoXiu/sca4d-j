package com.sca4d.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.config.AuthSettings;
import com.sca4d.auth.jwt.JwtSessionProjection;
import com.sca4d.auth.session.codec.PrincipalIdLoaders;
import com.sca4d.auth.session.codec.PrincipalLoaders;
import com.sca4d.auth.session.codec.ResourceIdLoaders;
import com.sca4d.auth.session.codec.ResourceLoaders;
import com.sca4d.auth.testing.SessionTestFixture;
import com.sca4d.core.api.authentication.AuthTokenMode;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.SessionIdentityLoader;
import com.sca4d.core.api.authentication.SessionTokens;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdLoader;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

/** 验证 Session 只由已认证账号建立，并要求身份装载结果唯一且一致。 */
class SessionServiceTest {

    @Test
    @DisplayName("登录没有成功认证时拒绝建立 Session")
    void rejectsLoginWithoutAuthentication() {
        SaTokenSessionManager sessions = mock(SaTokenSessionManager.class);
        SessionService service = service(List.of(), mock(ResourceLoaders.class), sessions);

        assertThatThrownBy(() -> service.establish(account(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("登录必须至少完成一种认证因素");
        verify(sessions, never()).establish(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("要求恰好一个 SessionIdentityLoader 认领账号")
    void requiresExactlyOneIdentityLoaderToClaimTheAccount() {
        Account account = account();
        SessionIdentity identity = identity(account);
        List<Authentication> authentications = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600))
                .authentications();
        SaTokenSessionManager sessions = mock(SaTokenSessionManager.class);
        ResourceLoaders resources = mock(ResourceLoaders.class);

        SessionService missing = service(List.of(), resources, sessions);
        assertThatThrownBy(() -> missing.establish(account, authentications))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("没有 SessionIdentityLoader 认领 Account");

        SessionIdentityLoader first = new FixedIdentityLoader(account, identity);
        SessionIdentityLoader second = new FixedIdentityLoader(account, identity);
        SessionService duplicate = service(List.of(first, second), resources, sessions);
        assertThatThrownBy(() -> duplicate.establish(account, authentications))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("多个 SessionIdentityLoader 认领 Account");

        verify(resources, never()).load(any());
        verify(sessions, never()).establish(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("拒绝 SessionIdentityLoader 返回其他账号")
    void rejectsAnIdentityLoaderReturningAnotherAccount() {
        Account account = account();
        Account another = new Account(23, account.subjectId(), account.realmKey());
        SessionService service = service(
                List.of(new FixedIdentityLoader(account, identity(another))),
                mock(ResourceLoaders.class),
                mock(SaTokenSessionManager.class));

        assertThatThrownBy(
                () -> service.establish(
                        account,
                        SessionTestFixture.session(SessionTestFixture.NOW.plusSeconds(3600))
                                .authentications()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SessionIdentityLoader 返回了其他 Account");
    }

    @Test
    @DisplayName("使用成功认证建立 Session 并签发 Token")
    void establishesSessionFromSuccessfulAuthentications() {
        Account account = account();
        SaTokenSessionManager sessions = mock(SaTokenSessionManager.class);
        when(sessions.currentGeneration(account.accountId())).thenReturn(7L);
        when(sessions.establish(any(), any(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn("session-token");
        JwtSessionProjection projection = mock(JwtSessionProjection.class);
        when(projection.issue(any(), org.mockito.ArgumentMatchers.eq("session-token")))
                .thenAnswer(invocation -> {
                    com.sca4d.core.api.authentication.Session session = invocation.getArgument(0);
                    return new SessionTokens(
                            AuthTokenMode.JWT,
                            "Bearer",
                            "access-token",
                            SessionTestFixture.NOW.plusSeconds(300),
                            "session-token",
                            session.id().value(),
                            session.expiresAt());
                });
        ResourceLoaders resources = mock(ResourceLoaders.class);
        when(resources.load(any())).thenReturn(java.util.Map.of(33L, List.of()));
        SessionService service = service(
                List.of(new FixedIdentityLoader(account, identity(account))),
                resources,
                sessions,
                projection);
        List<Authentication> authentications = SessionTestFixture.session(
                SessionTestFixture.NOW.plusSeconds(3600))
                .authentications();

        var establishment = service.establish(account, authentications);

        assertThat(establishment.session().account()).isEqualTo(account);
        assertThat(establishment.session().authentications()).containsExactlyElementsOf(authentications);
        assertThat(establishment.session().principalIdSets()).singleElement()
                .satisfies(principalIds -> assertThat(principalIds.ids().toArray()).containsExactly(34L));
        assertThat(establishment.tokens().accessToken()).isEqualTo("access-token");
    }

    private static SessionService service(
            List<SessionIdentityLoader> loaders,
            ResourceLoaders resources,
            SaTokenSessionManager sessions) {
        return service(loaders, resources, sessions, mock(JwtSessionProjection.class));
    }

    private static SessionService service(
            List<SessionIdentityLoader> loaders,
            ResourceLoaders resources,
            SaTokenSessionManager sessions,
            JwtSessionProjection projection) {
        Settings settings = mock(Settings.class);
        when(settings.get(AuthSettings.SESSION_TTL)).thenReturn(Duration.ofHours(1));
        return new SessionService(
                new SessionIdentityLoadService(loaders),
                new PrincipalLoaders(List.of(new FixedPrincipalLoader())),
                new PrincipalIdLoaders(List.of(new FixedPrincipalIdLoader())),
                resources,
                new ResourceIdLoaders(List.of(), Runnable::run),
                sessions,
                projection,
                settings,
                Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC));
    }

    private static Account account() {
        return new Account(22, 11, new RealmKey("test"));
    }

    private static SessionIdentity identity(Account account) {
        return new SessionIdentity(new Subject(account.subjectId(), SubjectType.HUMAN), account);
    }

    /** 返回测试预先配置的 SessionIdentity。 */
    @SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通测试类型。
    private static final class FixedIdentityLoader implements SessionIdentityLoader {
        private final Account supported;

        private final SessionIdentity identity;

        private FixedIdentityLoader(Account supported, SessionIdentity identity) {
            this.supported = supported;
            this.identity = identity;
        }

        @Override
        public boolean supports(@NotNull Account account) {
            return supported == account;
        }

        @Override
        public @NotNull SessionIdentity load(@NotNull Account account) {
            return identity;
        }
    }

    /** 为测试身份返回一个固定 Principal。 */
    private static final class FixedPrincipalLoader
            implements
                com.sca4d.core.api.authentication.principalsession.PrincipalLoader {

        @Override
        public boolean supports(@NotNull SessionIdentity identity) {
            return true;
        }

        @Override
        public @NotNull List<? extends Principal> load(@NotNull SessionIdentity identity) {
            return List.of(new SessionTestFixture.TestPrincipal(33, "principal"));
        }
    }

    /** 为测试身份返回一个只在 Session 保存标识的 Principal。 */
    private static final class FixedPrincipalIdLoader
            implements
                PrincipalIdLoader<SessionTestFixture.TestPrincipal> {

        @Override
        public boolean supports(@NotNull SessionIdentity identity) {
            return true;
        }

        @Override
        public @NotNull Class<SessionTestFixture.TestPrincipal> principalType() {
            return SessionTestFixture.TestPrincipal.class;
        }

        @Override
        public @NotNull List<@NotNull Long> load(@NotNull SessionIdentity identity) {
            return List.of(34L);
        }
    }
}
