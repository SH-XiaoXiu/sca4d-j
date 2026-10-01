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
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.redis.KeyRedisSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;

class RedisSessionGenerationTest {
    static {
        AppName.publish("sca4d-test");
    }

    @Test
    @DisplayName("Reads And Atomically Increments The Account Generation Before Notifying")
    void readsAndAtomicallyIncrementsTheAccountGenerationBeforeNotifying() {
        @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        Mockito.doReturn(new KeyRedisSerializer(List.of())).when(redis).getKeySerializer();
        @SuppressWarnings("unchecked")
        ValueOperations<Key, String> values = mock(ValueOperations.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        long accountId = 42;
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(Key.of(TreeCode.of("AUTH:SESSION_GENERATION"), 42L))).thenReturn(null, "1");
        when(values.increment(Key.of(TreeCode.of("AUTH:SESSION_GENERATION"), 42L))).thenReturn(1L);
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener));

        assertThat(generations.current(accountId)).isZero();
        generations.invalidate(accountId);
        assertThat(generations.current(accountId)).isEqualTo(1);

        verify(values).increment(Key.of(TreeCode.of("AUTH:SESSION_GENERATION"), 42L));
        verify(listener).invalidated(accountId);
    }

    @Test
    @DisplayName("Keeps The New Generation When A Notification Listener Fails")
    void keepsTheNewGenerationWhenANotificationListenerFails() {
        @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        Mockito.doReturn(new KeyRedisSerializer(List.of())).when(redis).getKeySerializer();
        @SuppressWarnings("unchecked")
        ValueOperations<Key, String> values = mock(ValueOperations.class);
        SessionInvalidationListener failing = mock(SessionInvalidationListener.class);
        SessionInvalidationListener succeeding = mock(SessionInvalidationListener.class);
        long accountId = 42;
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(Key.of(TreeCode.of("AUTH:SESSION_GENERATION"), 42L))).thenReturn(2L);
        doThrow(new IllegalStateException("notification failed")).when(failing).invalidated(accountId);
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(failing, succeeding));

        generations.invalidate(accountId);

        verify(failing).invalidated(accountId);
        verify(succeeding).invalidated(accountId);
    }

    @Test
    @DisplayName("Propagates Generation Store Failures Without Notifying")
    void propagatesGenerationStoreFailuresWithoutNotifying() {
        @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        Mockito.doReturn(new KeyRedisSerializer(List.of())).when(redis).getKeySerializer();
        @SuppressWarnings("unchecked")
        ValueOperations<Key, String> values = mock(ValueOperations.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        long accountId = 42;
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(Key.of(TreeCode.of("AUTH:SESSION_GENERATION"), 42L))).thenThrow(failure);
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener));

        assertThatThrownBy(() -> generations.invalidate(accountId)).isSameAs(failure);

        verify(listener, Mockito.never()).invalidated(accountId);
    }

    @Test
    @DisplayName("Invalidates Multiple Account Generations In One Redis Pipeline")
    @SuppressWarnings("unchecked")
    void invalidatesMultipleAccountGenerationsInOneRedisPipeline() {
        @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        Mockito.doReturn(new KeyRedisSerializer(List.of())).when(redis).getKeySerializer();
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.executePipelined(ArgumentMatchers.any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<Object> callback = invocation.getArgument(0);
                    callback.doInRedis(connection);
                    return List.of(1L, 1L);
                });
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener));

        generations.invalidate(List.of(42L, 43L, 42L));

        verify(redis, times(1)).executePipelined(ArgumentMatchers.any(RedisCallback.class));
        verify(strings).incr("SCA4D_TEST:AUTH:SESSION_GENERATION:42".getBytes(StandardCharsets.UTF_8));
        verify(strings).incr("SCA4D_TEST:AUTH:SESSION_GENERATION:43".getBytes(StandardCharsets.UTF_8));
        verify(listener).invalidated(42L);
        verify(listener).invalidated(43L);
    }

    @Test
    @DisplayName("Rejects Invalid Pipeline Generations Before Notifying Listeners")
    @SuppressWarnings("unchecked")
    void rejectsInvalidPipelineGenerationsBeforeNotifyingListeners() {
        @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        Mockito.doReturn(new KeyRedisSerializer(List.of())).when(redis).getKeySerializer();
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        SessionInvalidationListener listener = mock(SessionInvalidationListener.class);
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.executePipelined(ArgumentMatchers.any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<Object> callback = invocation.getArgument(0);
                    callback.doInRedis(connection);
                    return List.of(1L, -1L);
                });
        RedisSessionGeneration generations = new RedisSessionGeneration(
                redis,
                () -> List.of(listener));

        assertThatThrownBy(() -> generations.invalidate(List.of(42L, 43L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 返回了非法 Session 失效代次");

        verifyNoInteractions(listener);
    }
}
