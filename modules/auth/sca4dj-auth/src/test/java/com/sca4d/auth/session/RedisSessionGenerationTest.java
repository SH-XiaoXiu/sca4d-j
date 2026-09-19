package com.sca4d.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.SessionInvalidationListener;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;

class RedisSessionGenerationTest {

    @Test
    @DisplayName("Reads And Atomically Increments The Account Generation Before Notifying")
    void readsAndAtomicallyIncrementsTheAccountGenerationBeforeNotifying() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        long accountId = 42;
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("example-app:sca4dj:auth:session-generation:42")).thenReturn(null, "1");
        when(values.increment("example-app:sca4dj:auth:session-generation:42")).thenReturn(1L);
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener),
                new RuntimeNamespace("example-app"));

        assertThat(generations.current(accountId)).isZero();
        generations.invalidate(accountId);
        assertThat(generations.current(accountId)).isEqualTo(1);

        verify(values).increment("example-app:sca4dj:auth:session-generation:42");
        verify(listener).invalidated(accountId);
    }

    @Test
    @DisplayName("Keeps The New Generation When A Notification Listener Fails")
    void keepsTheNewGenerationWhenANotificationListenerFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        SessionInvalidationListener failing = mock(SessionInvalidationListener.class);
        SessionInvalidationListener succeeding = mock(SessionInvalidationListener.class);
        long accountId = 42;
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("example-app:sca4dj:auth:session-generation:42")).thenReturn(2L);
        doThrow(new IllegalStateException("notification failed")).when(failing).invalidated(accountId);
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(failing, succeeding),
                new RuntimeNamespace("example-app"));

        generations.invalidate(accountId);

        verify(failing).invalidated(accountId);
        verify(succeeding).invalidated(accountId);
    }

    @Test
    @DisplayName("Propagates Generation Store Failures Without Notifying")
    void propagatesGenerationStoreFailuresWithoutNotifying() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        long accountId = 42;
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("example-app:sca4dj:auth:session-generation:42")).thenThrow(failure);
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener),
                new RuntimeNamespace("example-app"));

        assertThatThrownBy(() -> generations.invalidate(accountId)).isSameAs(failure);

        verify(listener, org.mockito.Mockito.never()).invalidated(accountId);
    }

    @Test
    @DisplayName("Invalidates Multiple Account Generations In One Redis Pipeline")
    @SuppressWarnings("unchecked")
    void invalidatesMultipleAccountGenerationsInOneRedisPipeline() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.executePipelined(org.mockito.ArgumentMatchers.any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<Object> callback = invocation.getArgument(0);
                    callback.doInRedis(connection);
                    return List.of(1L, 1L);
                });
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener),
                new RuntimeNamespace("example-app"));

        generations.invalidate(List.of(42L, 43L, 42L));

        verify(redis, times(1)).executePipelined(org.mockito.ArgumentMatchers.any(RedisCallback.class));
        verify(strings).incr("example-app:sca4dj:auth:session-generation:42".getBytes(StandardCharsets.UTF_8));
        verify(strings).incr("example-app:sca4dj:auth:session-generation:43".getBytes(StandardCharsets.UTF_8));
        verify(listener).invalidated(42L);
        verify(listener).invalidated(43L);
    }

    @Test
    @DisplayName("Rejects Invalid Pipeline Generations Before Notifying Listeners")
    @SuppressWarnings("unchecked")
    void rejectsInvalidPipelineGenerationsBeforeNotifyingListeners() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.executePipelined(org.mockito.ArgumentMatchers.any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<Object> callback = invocation.getArgument(0);
                    callback.doInRedis(connection);
                    return List.of(1L, -1L);
                });
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener),
                new RuntimeNamespace("example-app"));

        assertThatThrownBy(() -> generations.invalidate(List.of(42L, 43L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 返回了非法 Session 失效代次");

        verifyNoInteractions(listener);
    }
}
