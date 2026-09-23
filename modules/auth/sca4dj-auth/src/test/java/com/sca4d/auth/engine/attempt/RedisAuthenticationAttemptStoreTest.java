package com.sca4d.auth.engine.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 验证认证尝试状态编码和分布式锁协作。
 */
class RedisAuthenticationAttemptStoreTest {

    @Test
    @DisplayName("认证尝试往返保留 Credential 最晚有效时间")
    void preservesCredentialValidityDeadline() {
        Instant authenticatedAt = Instant.parse("2026-08-12T00:00:00Z");
        Instant validUntil = authenticatedAt.plusSeconds(300);
        Account account = new Account(20, 10, new RealmKey("device"));
        Authentication authentication = new Authentication(
                account.accountId(),
                TreeCode.ofSlash("AUTHENTICATION/X509"),
                Set.of(AuthenticationFactorKind.POSSESSION),
                authenticatedAt);
        AuthenticationAttemptState state = new AuthenticationAttemptState(
                new AuthenticationAttemptId("attempt-1"),
                account,
                new AuthenticationActionCode("device.connect"),
                List.of(),
                List.of(
                        new VerifiedAuthentication(
                                account,
                                authentication,
                                "credential-1",
                                new Version(2),
                                validUntil)),
                validUntil.plusSeconds(60),
                true);
        AuthenticationAttemptCodec codec = new AuthenticationAttemptCodec(new ObjectMapper());

        AuthenticationAttemptState restored = codec.decode(codec.encode(state));

        assertThat(restored.verifications()).singleElement().satisfies(verification -> {
            assertThat(verification.authentication().accountId()).isEqualTo(authentication.accountId());
            assertThat(verification.authentication().methodCode()).isEqualTo(authentication.methodCode());
            assertThat(verification.authentication().factorKinds()).isEqualTo(authentication.factorKinds());
            assertThat(verification.authentication().authenticatedAt())
                    .isEqualTo(authentication.authenticatedAt());
            assertThat(verification.validUntil()).contains(validUntil);
        });
    }

    @Test
    @DisplayName("使用认证尝试标识取得自动续租分布式锁")
    void usesDistributedLockForAuthenticationAttempt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ImmediateDistributedLock distributedLock = new ImmediateDistributedLock();
        RedisAuthenticationAttemptStore store = new RedisAuthenticationAttemptStore(
                redis,
                new ObjectMapper(),
                Clock.systemUTC(),
                new RuntimeNamespace("example-app"),
                distributedLock);

        String result = store.withLease(new AuthenticationAttemptId("attempt-1"), () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(distributedLock.key.segments())
                .containsExactly("authentication", "attempt", "attempt-1");
        assertThat(distributedLock.waitTime).isEqualTo(Duration.ofSeconds(2));
    }

    /**
     * 立即执行任务并保留本次锁参数。
     */
    private static final class ImmediateDistributedLock implements DistributedLock {

        private LockKey key;

        private Duration waitTime;

        @Override
        public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
            this.key = key;
            this.waitTime = waitTime;
            return task.get();
        }
    }
}
