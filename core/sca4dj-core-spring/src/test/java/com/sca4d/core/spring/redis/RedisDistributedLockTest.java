package com.sca4d.core.spring.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 验证 RedisDistributedLock 使用当前 App 命名空间获取 Redisson 锁，覆盖同线程重入、所有权丢失、异步取消和延迟取得锁的行为。 */
class RedisDistributedLockTest {
    // 锁键前缀由进程级 AppName 决定，必须在任何测试访问 Redis 名称之前发布为 sca4d-test。
    static {
        AppName.publish("sca4d-test");
    }

    @Test
    @DisplayName("Uses The Application Namespace And Lock Key Segments")
    void usesTheApplicationNamespaceAndLockKeySegments() throws Exception {
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("SCA4D_TEST:LOCK:PLATFORM:PROFILE:42")).thenReturn(lock);
        when(lock.tryLock(25, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedisDistributedLock distributedLock = new RedisDistributedLock(
                redisson);

        String result = distributedLock.execute(
                new LockKey(TreeCode.of("PLATFORM:PROFILE"), 42L),
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
        when(redisson.getLock("SCA4D_TEST:LOCK:IOT:DEVICE:42")).thenReturn(lock);
        when(lock.tryLock(25, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedisDistributedLock distributedLock = new RedisDistributedLock(
                redisson);
        LockKey key = new LockKey(TreeCode.of("IOT:DEVICE"), 42L);

        String result = distributedLock.execute(
                key,
                Duration.ofMillis(25),
                () -> distributedLock.execute(key, Duration.ofMillis(25), () -> "done"));

        assertEquals("done", result);
        verify(redisson, times(1)).getLock("SCA4D_TEST:LOCK:IOT:DEVICE:42");
        verify(lock, times(1)).tryLock(25, TimeUnit.MILLISECONDS);
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("同步任务结束时锁所有权丢失必须报告，原业务异常仍为主异常")
    void reportsLostOwnershipWithoutMaskingTheTaskFailure() throws Exception {
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("SCA4D_TEST:LOCK:LOST:42")).thenReturn(lock);
        when(lock.tryLock(25, TimeUnit.MILLISECONDS)).thenReturn(true);
        LockKey key = new LockKey(TreeCode.ofSegment("LOST"), 42L);
        RedisDistributedLock distributedLock = new RedisDistributedLock(redisson);

        assertTrue(
                assertThrows(
                        IllegalStateException.class,
                        () -> distributedLock.execute(key, Duration.ofMillis(25), () -> "committed"))
                        .getMessage()
                        .contains("失去分布式锁所有权"));
        IllegalArgumentException taskFailure = new IllegalArgumentException("task");
        assertSame(
                taskFailure,
                assertThrows(
                        IllegalArgumentException.class,
                        () -> distributedLock.execute(key, Duration.ofMillis(25), () -> {
                            throw taskFailure;
                        })));
        assertEquals(1, taskFailure.getSuppressed().length);
        verify(lock, times(0)).unlock();
    }

    @Test
    @DisplayName("异步锁持有到目标完成，取消结果不提前释放正在运行的目标")
    void holdsAsyncLockUntilTargetActuallyCompletes() {
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("SCA4D_TEST:LOCK:ASYNC:42")).thenReturn(lock);
        when(lock.tryLockAsync(eq(25L), eq(-1L), eq(TimeUnit.MILLISECONDS), anyLong()))
                .thenReturn(new CompletableFutureWrapper<>(true));
        when(lock.unlockAsync(anyLong())).thenReturn(CompletableFutureWrapper.completedNull());
        RedisDistributedLock distributedLock = new RedisDistributedLock(redisson);
        CompletableFuture<String> target = new CompletableFuture<>();

        CompletionStage<String> result = distributedLock.executeAsync(
                new LockKey(TreeCode.ofSegment("ASYNC"), 42L),
                Duration.ofMillis(25),
                () -> target);
        assertFalse(result.toCompletableFuture().isDone());
        verify(lock, times(0)).unlockAsync(anyLong());
        result.toCompletableFuture().cancel(true);
        assertFalse(target.isDone());
        verify(lock, times(0)).unlockAsync(anyLong());
        target.complete("done");
        assertTrue(result.toCompletableFuture().isCancelled());
        verify(lock, times(1)).unlockAsync(anyLong());
    }

    @Test
    @DisplayName("延迟取得异步锁时仍在发起线程启动目标")
    void startsTargetOnTheCallingThreadAfterDelayedAcquisition() throws Exception {
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("SCA4D_TEST:LOCK:DELAYED:42")).thenReturn(lock);
        CompletableFuture<Boolean> acquired = new CompletableFuture<>();
        CountDownLatch waiting = new CountDownLatch(1);
        when(lock.tryLockAsync(eq(25L), eq(-1L), eq(TimeUnit.MILLISECONDS), anyLong()))
                .thenAnswer(ignored -> {
                    waiting.countDown();
                    return new CompletableFutureWrapper<>(acquired);
                });
        when(lock.unlockAsync(anyLong())).thenReturn(CompletableFutureWrapper.completedNull());
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> started = new CompletableFuture<>();
            var result = caller.submit(() -> {
                String initiatingThread = Thread.currentThread().getName();
                CompletionStage<String> completed = new RedisDistributedLock(redisson).executeAsync(
                        new LockKey(TreeCode.ofSegment("DELAYED"), 42L),
                        Duration.ofMillis(25),
                        () -> {
                            started.complete(Thread.currentThread().getName());
                            return CompletableFuture.completedFuture("done");
                        });
                assertEquals(initiatingThread, started.join());
                return completed.toCompletableFuture().join();
            });
            assertTrue(waiting.await(2, TimeUnit.SECONDS));
            assertFalse(started.isDone());
            acquired.complete(true);
            assertEquals("done", result.get());
            verify(lock).unlockAsync(anyLong());
        } finally {
            caller.shutdownNow();
        }
    }
}
