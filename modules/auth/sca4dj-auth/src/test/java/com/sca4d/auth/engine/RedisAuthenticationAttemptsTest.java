package com.sca4d.auth.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.auth.config.AuthSettings;
import com.sca4d.auth.engine.attempt.AuthenticationAttemptState;
import com.sca4d.auth.engine.attempt.RedisAuthenticationAttemptStore;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.AuthenticationMethod;
import com.sca4d.core.api.authentication.AuthenticationPolicy;
import com.sca4d.core.api.authentication.AuthenticationProgress;
import com.sca4d.core.api.authentication.AuthenticationRequirement;
import com.sca4d.core.api.authentication.AuthenticationResult;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.setting.Settings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 验证认证引擎按动作策略计算不同因素、复用已有认证并单次消费结果。
 */
class RedisAuthenticationAttemptsTest {

    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");

    private static final Account ACCOUNT = new Account(20, 10, new RealmKey("platform"));

    private static final AuthenticationActionCode ACTION = new AuthenticationActionCode("account.login");

    @Test
    @DisplayName("单因素认证完成后只能消费一次")
    void completesSingleFactorAndConsumesItOnce() {
        Method password = new Method("password", AuthenticationFactorKind.KNOWLEDGE, true);
        Fixture fixture = fixture(1, null, password);

        AuthenticationProgress progress = fixture.engine.start(ACTION, submission(password));

        assertThat(progress.satisfied()).isTrue();
        assertThat(fixture.engine.consume(progress.attemptId()).authentications()).hasSize(1);
        assertThatThrownBy(() -> fixture.engine.consume(progress.attemptId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TOTP 和 Passkey 同属持有因素，只计为一个因素")
    void countsTotpAndPasskeyAsOnePossessionFactor() {
        Method password = new Method("password", AuthenticationFactorKind.KNOWLEDGE, true);
        Method totp = new Method("totp", AuthenticationFactorKind.POSSESSION, false);
        Method passkey = new Method("passkey", AuthenticationFactorKind.POSSESSION, false);
        Method inherence = new Method("biometric", AuthenticationFactorKind.INHERENCE, false);
        Fixture fixture = fixture(3, null, password, totp, passkey, inherence);

        AuthenticationProgress progress = fixture.engine.start(ACTION, submission(password));
        progress = fixture.engine.submit(progress.attemptId(), submission(totp));
        AuthenticationProgress repeatedKind = fixture.engine.submit(progress.attemptId(), submission(passkey));
        AuthenticationProgress completed = fixture.engine.submit(repeatedKind.attemptId(), submission(inherence));

        assertThat(repeatedKind.remainingFactorCount()).isEqualTo(1);
        assertThat(completed.satisfied()).isTrue();
        assertThat(fixture.engine.consume(completed.attemptId()).authentications()).hasSize(4);
    }

    @Test
    @DisplayName("策略允许时只复用最大年龄内的 Session 认证")
    void reusesOnlyFreshSessionAuthenticationsWhenAllowed() {
        Method totp = new Method("totp", AuthenticationFactorKind.POSSESSION, false);
        Authentication freshPassword = authentication(
                "password",
                AuthenticationFactorKind.KNOWLEDGE,
                NOW.minus(Duration.ofMinutes(4)));
        Authentication staleInherence = authentication(
                "biometric",
                AuthenticationFactorKind.INHERENCE,
                NOW.minus(Duration.ofMinutes(6)));
        Fixture fixture = fixture(2, Duration.ofMinutes(5), totp);

        AuthenticationProgress progress = fixture.engine.start(
                ACCOUNT,
                ACTION,
                List.of(freshPassword, staleInherence));
        AuthenticationProgress completed = fixture.engine.submit(progress.attemptId(), submission(totp));

        assertThat(progress.remainingFactorCount()).isEqualTo(1);
        assertThat(fixture.engine.consume(completed.attemptId()).authentications())
                .extracting(authentication -> authentication.methodCode().value())
                .containsExactly("password", "totp");
    }

    @Test
    @DisplayName("策略禁止复用时忽略全部 Session 认证")
    void ignoresSessionAuthenticationsWhenReuseIsDisabled() {
        Method password = new Method("password", AuthenticationFactorKind.KNOWLEDGE, false);
        Fixture fixture = fixture(1, null, password);

        AuthenticationProgress progress = fixture.engine.start(
                ACCOUNT,
                ACTION,
                List.of(authentication("password", AuthenticationFactorKind.KNOWLEDGE, NOW)));

        assertThat(progress.satisfied()).isFalse();
        assertThat(progress.remainingFactorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("已知账号的零因素动作立即满足")
    void satisfiesZeroFactorActionImmediately() {
        Fixture fixture = fixture(0, null);

        AuthenticationProgress progress = fixture.engine.start(ACCOUNT, ACTION, List.of());

        assertThat(progress.satisfied()).isTrue();
        assertThat(fixture.engine.consume(progress.attemptId()).authentications()).isEmpty();
    }

    @Test
    @DisplayName("Challenge 方法可以读取有效认证尝试已经确定的账号")
    void exposesAttemptAccountForChallengeMethods() {
        Method password = new Method("password", AuthenticationFactorKind.KNOWLEDGE, true);
        Method totp = new Method("totp", AuthenticationFactorKind.POSSESSION, false);
        Fixture fixture = fixture(2, null, password, totp);

        AuthenticationProgress progress = fixture.engine.start(ACTION, submission(password));

        assertThat(fixture.engine.account(progress.attemptId())).isEqualTo(ACCOUNT);
    }

    @Test
    @DisplayName("提交材料后使用最新时间评估认证结果")
    void evaluatesSubmittedAuthenticationUsingCurrentTime() {
        Method password = new Method("password", AuthenticationFactorKind.KNOWLEDGE, true);
        Method totp = new Method(
                "totp",
                AuthenticationFactorKind.POSSESSION,
                false,
                NOW.plusNanos(2));
        Fixture fixture = fixture(
                2,
                null,
                new AdvancingClock(NOW, NOW.plusNanos(1), NOW.plusNanos(2)),
                password,
                totp);

        AuthenticationProgress progress = fixture.engine.start(ACTION, submission(password));
        AuthenticationProgress completed = fixture.engine.submit(progress.attemptId(), submission(totp));

        assertThat(completed.satisfied()).isTrue();
    }

    @Test
    @DisplayName("消费结果使用同一时刻判断并返回可复用认证")
    void consumesReusableAuthenticationAtOneConsistentInstant() {
        Authentication reusable = authentication(
                "password",
                AuthenticationFactorKind.KNOWLEDGE,
                NOW);
        Fixture fixture = fixture(
                1,
                Duration.ofNanos(1),
                new AdvancingClock(NOW, NOW.plusNanos(1), NOW.plusNanos(2)));

        AuthenticationProgress progress = fixture.engine.start(
                ACCOUNT,
                ACTION,
                List.of(reusable));
        AuthenticationResult result = fixture.engine.consume(progress.attemptId());

        assertThat(result.authentications()).containsExactly(reusable);
    }

    private static Fixture fixture(
            int factorCount,
            Duration reusableMaximumAge,
            Method... methods) {
        return fixture(
                factorCount,
                reusableMaximumAge,
                Clock.fixed(NOW, ZoneOffset.UTC),
                methods);
    }

    private static Fixture fixture(
            int factorCount,
            Duration reusableMaximumAge,
            Clock clock,
            Method... methods) {
        RedisAuthenticationAttemptStore store = mock(RedisAuthenticationAttemptStore.class);
        AtomicReference<AuthenticationAttemptState> state = new AtomicReference<>();
        doAnswer(invocation -> {
            state.set(invocation.getArgument(0));
            return null;
        })
                .when(store)
                .create(any());
        doAnswer(invocation -> {
            state.set(invocation.getArgument(0));
            return null;
        })
                .when(store)
                .save(any());
        doAnswer(invocation -> {
            state.set(null);
            return null;
        })
                .when(store)
                .delete(any());
        when(store.loadById(any())).thenAnswer(invocation -> {
            AuthenticationAttemptState current = state.get();
            if (current == null) {
                throw new IllegalStateException("认证尝试不存在或已过期");
            }
            return current;
        });
        when(store.withLease(any(), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
        Set<TreeCode> allowed = Arrays.stream(methods)
                .map(Method::code)
                .collect(Collectors.toUnmodifiableSet());
        AuthenticationPolicy policy = new AuthenticationPolicy() {
            @Override
            public @NotNull AuthenticationRequirement requirementFor(
                    @NotNull Account account,
                    @NotNull AuthenticationActionCode actionCode) {
                return new AuthenticationRequirement(factorCount, reusableMaximumAge);
            }

            @Override
            public @NotNull Set<TreeCode> allowedMethods(
                    @NotNull Account account,
                    @NotNull AuthenticationActionCode actionCode,
                    @NotNull List<Authentication> completedAuthentications) {
                return allowed;
            }
        };
        Settings settings = mock(Settings.class);
        when(settings.get(AuthSettings.ATTEMPT_TTL)).thenReturn(Duration.ofMinutes(5));
        RedisAuthenticationAttempts engine = new RedisAuthenticationAttempts(
                new AuthenticationMethods(List.of(methods)),
                store,
                policy,
                settings,
                clock);
        return new Fixture(engine);
    }

    private static AuthenticationSubmission<String> submission(Method method) {
        return new AuthenticationSubmission<>(method.code(), String.class, "proof");
    }

    private static Authentication authentication(
            String method,
            AuthenticationFactorKind kind,
            Instant authenticatedAt) {
        return new Authentication(
                ACCOUNT.accountId(),
                authenticationMethodCode(method),
                Set.of(kind),
                authenticatedAt);
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class Fixture {
        private final RedisAuthenticationAttempts engine;

        private Fixture(RedisAuthenticationAttempts engine) {
            this.engine = engine;
        }
    }

    /**
     * 提供测试所需的固定认证方法行为。
     */
    @SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通测试类型。
    private static final class Method implements AuthenticationMethod<String> {
        private final TreeCode code;

        private final AuthenticationFactorKind kind;

        private final boolean identifiesAccount;

        private final Instant authenticatedAt;

        private Method(String code, AuthenticationFactorKind kind, boolean identifiesAccount) {
            this(code, kind, identifiesAccount, NOW);
        }

        private Method(
                String code,
                AuthenticationFactorKind kind,
                boolean identifiesAccount,
                Instant authenticatedAt) {
            this.code = authenticationMethodCode(code);
            this.kind = kind;
            this.identifiesAccount = identifiesAccount;
            this.authenticatedAt = authenticatedAt;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public @NotNull Class<String> proofType() {
            return String.class;
        }

        @Override
        public @NotNull Set<AuthenticationFactorKind> factorKinds() {
            return Set.of(kind);
        }

        @Override
        public boolean canIdentifyAccount() {
            return identifiesAccount;
        }

        @Override
        public boolean available(@NotNull Account account) {
            return true;
        }

        @Override
        public @NotNull VerifiedAuthentication authenticate(
                @Nullable Account account,
                @NotNull String proof) {
            Account checkedAccount = account == null ? ACCOUNT : account;
            return new VerifiedAuthentication(
                    checkedAccount,
                    new Authentication(checkedAccount.accountId(), code, Set.of(kind), authenticatedAt),
                    code.value(),
                    new Version(0),
                    null);
        }

        @Override
        public void verifyCurrent(
                @NotNull Account account,
                @NotNull VerifiedAuthentication authentication) {
        }

        @Override
        public void eraseAccount(long accountId) {
        }
    }

    /**
     * @return 测试认证方法使用的稳定编码
     */
    private static @NotNull TreeCode authenticationMethodCode(@NotNull String value) {
        return TreeCode.ofSlash("AUTHENTICATION/" + value.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
    }

    /**
     * 在每次读取后推进到下一个预设时间。
     */
    private static final class AdvancingClock extends Clock {
        private final Instant[] instants;

        private int index;

        private AdvancingClock(Instant... instants) {
            this.instants = instants.clone();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("测试时钟只支持 UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            int current = Math.min(index, instants.length - 1);
            index++;
            return instants[current];
        }
    }
}
