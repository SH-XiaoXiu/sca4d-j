package com.sca4d.common.concurrent.lock;

import com.sca4d.common.exception.CommonErrorCode;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的分布式锁执行器。
 */
public class RedissonDistributedLockExecutor implements DistributedLockExecutor {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedLockExecutor.class);

    private final RedissonClient redissonClient;

    /**
     * 创建使用 Redisson 所有权和 watchdog 含义的集群锁执行器。
     *
     * @param redissonClient 应用共享的 Redisson 客户端
     */
    public RedissonDistributedLockExecutor(@NotNull RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T execute(
            @NotNull String key,
            @NotNull Duration waitTime,
            @NotNull Duration leaseTime,
            @NotNull LockedOperation<T> operation)
            throws Throwable {
        if (waitTime.isNegative()) {
            throw new IllegalArgumentException("分布式锁等待时间不能为负数");
        }
        if (leaseTime.isNegative()) {
            throw new IllegalArgumentException("分布式锁租约时间不能为负数");
        }
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            acquired = leaseTime.isZero()
                    ? lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS)
                    : lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new LockBusyException();
            }
            return operation.execute();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LockBusyException(CommonErrorCode.LOCK_INTERRUPTED, exception);
        } finally {
            if (acquired) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (RuntimeException exception) {
                    // 业务可能已经提交；解锁故障由 watchdog 或固定租约最终释放，不能改变真实业务结果。
                    log.error(
                            "分布式锁释放失败，等待自动过期 | lockKey={}",
                            key,
                            exception);
                }
            }
        }
    }
}
