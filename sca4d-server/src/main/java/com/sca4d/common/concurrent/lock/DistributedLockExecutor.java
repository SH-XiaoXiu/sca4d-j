package com.sca4d.common.concurrent.lock;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 在集群共享锁所有权内执行一个业务操作的底层扩展点。 */
public interface DistributedLockExecutor {

    /**
     * 等待并获得指定锁后执行操作，最终仅由当前所有者释放锁。
     *
     * @param key 跨节点竞争同一业务资源的固定锁名称
     * @param waitTime 获得锁前允许阻塞的非负时间
     * @param leaseTime 零表示实现应自动续租，正数表示固定租约
     * @param operation 只允许在持有锁期间执行的业务操作
     * @return 操作返回值
     * @param <T> 操作返回值类型
     * @throws Throwable 未获得锁、线程中断或业务操作失败时抛出
     */
    <T> T execute(
            @NotNull String key,
            @NotNull Duration waitTime,
            @NotNull Duration leaseTime,
            @NotNull LockedOperation<T> operation)
            throws Throwable;
}
