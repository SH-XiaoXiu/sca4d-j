package com.sca4d.core.api.concurrent;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 保证相同锁键在所有参与协调的应用实例中互斥执行的分布式锁（Distributed Lock）。
 *
 * <p>实现必须先取得锁再执行完整任务，并在任务成功或失败后释放锁。超过等待时间仍未取得锁时，实现必须抛出异常，不能执行业务任务。
 */
public interface DistributedLock {

    /**
     * 取得指定分布式锁并执行完整任务。
     *
     * @param key 锁键
     * @param waitTime 最大等待时间
     * @param task 完整业务任务
     * @param <T> 任务结果类型
     * @return 任务结果
     */
    <T> T execute(LockKey key, Duration waitTime, Supplier<T> task);

    /**
     * 在调用线程等待取得锁并启动异步任务，在任务真正完成后释放锁；取消返回值不表示任务已经停止。
     * 不支持异步持锁的实现必须拒绝，不能在目标尚未完成时释放锁。
     *
     * @param key 锁键
     * @param waitTime 最大等待时间
     * @param task 仅在取得锁后调用一次的异步任务
     * @return 锁内任务完成且锁已释放后的结果
     */
    default <T> CompletionStage<T> executeAsync(
            LockKey key,
            Duration waitTime,
            Supplier<? extends CompletionStage<T>> task) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("当前分布式锁不支持异步持锁"));
    }
}
