package com.sca4d.common.concurrent.lock;

import com.sca4d.config.properties.Sca4djConcurrentProperties;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * 分布式锁模板。
 *
 * <p>业务优先使用 {@link DistributedLock} 注解；需要在同一把锁内执行多个操作时，可以直接注入该模板。
 */
public class DistributedLockTemplate {

    private final DistributedLockExecutor executor;
    private final Sca4djConcurrentProperties properties;

    /**
     * 创建供注解切面和直接调用共同使用的锁模板。
     *
     * @param executor 具体 Redisson 所有权执行器
     * @param properties 全局默认等待和租约配置
     */
    public DistributedLockTemplate(
            @NotNull DistributedLockExecutor executor, @NotNull Sca4djConcurrentProperties properties) {
        this.executor = executor;
        this.properties = properties;
    }

    /**
     * 使用全局默认等待时间和租约策略执行操作。
     *
     * @param key 跨节点竞争同一业务资源的固定锁名称
     * @param operation 只允许在持有锁期间执行的业务操作
     * @return 操作返回值
     * @param <T> 操作返回值类型
     * @throws Throwable 未获得锁、线程中断或业务操作失败时抛出
     */
    public <T> T execute(@NotNull String key, @NotNull LockedOperation<T> operation) throws Throwable {
        return execute(key, properties.getLock().getWaitTime(), properties.getLock().getLeaseTime(), operation);
    }

    /**
     * 使用指定等待时间和租约策略执行操作。
     *
     * @param key 跨节点竞争同一业务资源的固定锁名称
     * @param waitTime 获得锁前允许阻塞的非负时间
     * @param leaseTime 零表示 watchdog 自动续租，正数表示固定租约
     * @param operation 只允许在持有锁期间执行的业务操作
     * @return 操作返回值
     * @param <T> 操作返回值类型
     * @throws Throwable 未获得锁、线程中断或业务操作失败时抛出
     */
    public <T> T execute(
            @NotNull String key,
            @NotNull Duration waitTime,
            @NotNull Duration leaseTime,
            @NotNull LockedOperation<T> operation)
            throws Throwable {
        return executor.execute(key, waitTime, leaseTime, operation);
    }
}
