package com.sca4d.core.api.concurrent;

import java.time.Duration;
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
}
