package com.sca4d.core.spring.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

class RedisDistributedLockTest {

    @Test
    @DisplayName("Uses The Application Namespace And Lock Key Segments")
    void usesTheApplicationNamespaceAndLockKeySegments() throws Exception {
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("example-app:sca4dj:lock:platform:profile:42")).thenReturn(lock);
        when(lock.tryLock(25, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedisDistributedLock distributedLock = new RedisDistributedLock(
                redisson,
                new RuntimeNamespace("example-app"));

        String result = distributedLock.execute(
                new LockKey("platform", "profile", "42"),
                Duration.ofMillis(25),
                () -> "done");

        assertEquals("done", result);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("同一线程嵌套相同锁键只访问一次 Redisson")
    void reusesTheLockAlreadyHeldByTheCurrentThread() throws Exception {
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("example-app:sca4dj:lock:iot:device:42")).thenReturn(lock);
        when(lock.tryLock(25, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedisDistributedLock distributedLock = new RedisDistributedLock(
                redisson,
                new RuntimeNamespace("example-app"));
        LockKey key = new LockKey("iot", "device", "42");

        String result = distributedLock.execute(
                key,
                Duration.ofMillis(25),
                () -> distributedLock.execute(key, Duration.ofMillis(25), () -> "done"));

        assertEquals("done", result);
        verify(redisson, times(1)).getLock("example-app:sca4dj:lock:iot:device:42");
        verify(lock, times(1)).tryLock(25, TimeUnit.MILLISECONDS);
        verify(lock, times(1)).unlock();
    }
}
