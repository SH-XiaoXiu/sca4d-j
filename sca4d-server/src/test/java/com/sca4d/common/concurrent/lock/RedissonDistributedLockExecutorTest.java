package com.sca4d.common.concurrent.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** 验证 Redisson 锁的等待、执行、异常传播和释放。 */
class RedissonDistributedLockExecutorTest {

    @Test
    void shouldUseWatchdogWhenLeaseTimeIsZero() throws Throwable {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lock:test")).thenReturn(lock);
        when(lock.tryLock(2000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);

        String result = executor.execute(
                "lock:test", Duration.ofSeconds(2), Duration.ZERO, () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock).tryLock(2000L, TimeUnit.MILLISECONDS);
        verify(lock, never()).tryLock(2000L, 30000L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void shouldUseExplicitFixedLeaseWhenConfigured() throws Throwable {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lock:test")).thenReturn(lock);
        when(lock.tryLock(2000L, 30000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);

        executor.execute("lock:test", Duration.ofSeconds(2), Duration.ofSeconds(30), () -> "done");

        verify(lock).tryLock(2000L, 30000L, TimeUnit.MILLISECONDS);
        verify(lock, never()).tryLock(2000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void shouldPreserveBusinessResultWhenUnlockFails() throws Throwable {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lock:test")).thenReturn(lock);
        when(lock.tryLock(2000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        Mockito.doThrow(new IllegalStateException("redis unavailable"))
                .when(lock)
                .unlock();
        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);

        String result = executor.execute(
                "lock:test", Duration.ofSeconds(2), Duration.ZERO, () -> "done");

        assertThat(result).isEqualTo("done");
    }
}
