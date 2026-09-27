package com.sca4d.core.spring.redis;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 使用 Redisson watchdog 在多个应用实例之间互斥执行同一业务键的任务。 */
public final class RedisDistributedLock implements DistributedLock {

    private final RedissonClient redisson;

    private final ThreadLocal<Set<LockKey>> heldLocks = ThreadLocal.withInitial(HashSet::new);

    /**
     * 创建使用当前 App 隔离名称的分布式锁。
     *
     * @param redisson Redisson 客户端
     */
    public RedisDistributedLock(@NotNull RedissonClient redisson) {
        this.redisson = Objects.requireNonNull(redisson, "Redisson 客户端不能为空");
    }

    /**
     * 等待取得锁后执行任务，任务执行期间由 Redisson watchdog 续租。
     *
     * @param key 锁键
     * @param waitTime 最大等待时间；必须大于等于零
     * @param task 只在取得锁后执行的任务
     * @param <T> 任务结果类型
     * @return 任务结果
     * @throws BusinessException 等待超时或线程被中断时抛出，任务不会执行
     */
    @Override
    public <T> T execute(@NotNull LockKey key, @NotNull Duration waitTime, @NotNull Supplier<T> task) {
        // 校验锁请求并把业务锁键放入当前 App 隔离名称，避免不同应用共用 Redis 时互相阻塞。
        LockKey checkedKey = Objects.requireNonNull(key, "锁键不能为空");
        Duration checkedWaitTime = Objects.requireNonNull(waitTime, "锁等待时间不能为空");
        Supplier<T> checkedTask = Objects.requireNonNull(task, "锁内任务不能为空");
        if (checkedWaitTime.isNegative()) {
            throw new IllegalArgumentException("锁等待时间不能为负数");
        }

        LockKey lockName = checkedKey;
        Set<LockKey> currentLocks = heldLocks.get();

        // 同一线程已经持有同名锁时直接执行内层任务，由最外层调用负责最终释放。
        if (currentLocks.contains(lockName)) {
            return checkedTask.get();
        }

        // 只等待调用方允许的时间；中断时恢复线程中断标记，且不执行受保护任务。
        RLock lock = redisson.getLock(lockName.format(COLON));
        try {
            if (!lock.tryLock(checkedWaitTime.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BusinessException(CoreErrorCode.CONFLICT);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CoreErrorCode.UNAVAILABLE, exception);
        }

        // 取得锁后登记线程内持有状态，任务结束时先释放 Redis 锁，再清理线程本地状态。
        currentLocks.add(lockName);
        try {
            return checkedTask.get();
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } finally {
                currentLocks.remove(lockName);
                if (currentLocks.isEmpty()) {
                    heldLocks.remove();
                }
            }
        }
    }
}
