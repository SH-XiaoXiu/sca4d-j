package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.email.EmailCodeChallenge;
import com.sca4d.authentication.email.EmailCodeChallengeState;
import com.sca4d.authentication.email.store.RedisEmailCodeChallengeStore;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.util.List;

/** 验证邮件验证码 Challenge 的同槽 key 和 Lua 状态推进契约。 */
class RedisEmailCodeChallengeStoreTest {

    private final RedisScriptExecutor scripts = mock(RedisScriptExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Sca4djAuthenticationProperties properties = new Sca4djAuthenticationProperties();
    private final RedisEmailCodeChallengeStore store = new RedisEmailCodeChallengeStore(scripts, objectMapper, properties);

    @Test
    void shouldCreateChallengeWithActiveAndCooldownKeys() {
        String prefix = "sca4dj:authentication:email-code:{flow-1}";
        List<String> keys = List.of(
                prefix + ":challenge:challenge-1", prefix + ":active", prefix + ":cooldown");
        when(scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                eq(keys),
                anyString(),
                eq("300000"),
                eq("60000")))
                .thenReturn(1L);

        EmailCodeChallenge challenge = new EmailCodeChallenge()
                .setAuthenticationId("flow-1")
                .setChallengeId("challenge-1")
                .setState(EmailCodeChallengeState.PENDING)
                .setVersion(0L)
                .setAttemptsRemaining(5)
                .setCodeHash("hash");
        assertThat(store.create(challenge, Duration.ofMinutes(5), Duration.ofMinutes(1))).isTrue();
        verify(scripts).execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                eq(keys),
                anyString(),
                eq("300000"),
                eq("60000"));
    }

    @Test
    void shouldRejectNonPositiveDurations() {
        EmailCodeChallenge challenge = new EmailCodeChallenge()
                .setAuthenticationId("flow-1")
                .setChallengeId("challenge-1")
                .setState(EmailCodeChallengeState.PENDING)
                .setVersion(0L)
                .setAttemptsRemaining(5)
                .setCodeHash("hash");
        assertThatThrownBy(() -> store.create(challenge, Duration.ZERO, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(challenge, Duration.ofMinutes(5), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReadAdvanceAndCancelChallenge() throws Exception {
        EmailCodeChallenge challenge = new EmailCodeChallenge()
                .setAuthenticationId("flow-1")
                .setChallengeId("challenge-1")
                .setState(EmailCodeChallengeState.PENDING)
                .setVersion(0L)
                .setAttemptsRemaining(5)
                .setCodeHash("hash");
        String prefix = "sca4dj:authentication:email-code:{flow-1}";
        List<String> keys = List.of(
                prefix + ":challenge:challenge-1", prefix + ":active", prefix + ":cooldown");
        String challengeKey = keys.getFirst();
        when(scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<String>>any(),
                eq(List.of(challengeKey))))
                .thenReturn(objectMapper.writeValueAsString(challenge));
        when(scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                eq(List.of(challengeKey, keys.get(1))),
                eq("0"),
                eq("PENDING"),
                anyString(),
                eq("1")))
                .thenReturn(1L);

        assertThat(store.find("flow-1", "challenge-1")).usingRecursiveComparison().isEqualTo(challenge);
        assertThat(store.advance(challenge, 0, EmailCodeChallengeState.PENDING, true)).isTrue();
        store.cancel("flow-1", "challenge-1");

        verify(scripts).execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(), eq(keys));
    }
}
