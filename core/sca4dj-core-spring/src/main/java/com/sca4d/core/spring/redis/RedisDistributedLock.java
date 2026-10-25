package com.sca4d.core.spring.redis;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** 使用 Redisson watchdog 在多个应用实例之间互斥执行同一业务键的任务。 */
public class RedisDistributedLock implements DistributedLock {

    private static final AtomicLong ASYNC_OWNERS = new AtomicLong(Long.MIN_VALUE);

    private final RedissonClient redisson;

    private final ThreadLocal<Set<LockKey>> heldLocks = ThreadLocal.withInitial(HashSet::new);

    /**
     * 创建分布式锁。
     *
     * <p>锁名称直接由 {@code LockKey} 按 {@code COLON} 格式生成，本类不再加入 App 名称前缀。
     *
     * @param redisson Redisson 客户端
     */
    public RedisDistributedLock(@NotNull RedissonClient redisson) {
        this.redisson = Objects.requireNonNull(redisson, "Redisson 客户端不能为空");
    }

    /**
     * 在当前线程等待取得锁后执行任务，任务执行期间由 Redisson watchdog 续租。
     *
     * <p>同一线程内对同一个锁键嵌套调用时直接执行任务，不再次获取 Redis 锁，由最外层调用负责释放。
     * 任务正常结束后，若当前线程已不再持有该 Redis 锁，抛出 IllegalStateException。
     * 任务抛出的 RuntimeException 或 Error 原样传播；此时解锁失败只会作为 suppressed 附加到任务异常上。
     *
     * @param key 锁键
     * @param waitTime 最大等待时间；必须大于等于零
     * @param task 只在取得锁后执行的任务
     * @param <T> 任务结果类型
     * @return 任务结果
     * @throws BusinessException 等待超时返回 CONFLICT；等待时线程被中断返回 UNAVAILABLE，任务不会执行
     * @throws IllegalArgumentException 等待时间为负数时抛出
     * @throws IllegalStateException 任务结束时已失去锁所有权时抛出，任务已经执行
     */
    @Override
    public <T> T execute(@NotNull LockKey key, @NotNull Duration waitTime, @NotNull Supplier<T> task) {
        // 校验锁请求；锁名称与 LockKey 的格式一致，不额外加入 App 前缀。
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
        Throwable taskFailure = null;
        try {
            return checkedTask.get();
        } catch (RuntimeException | Error failure) {
            taskFailure = failure;
            throw failure;
        } finally {
            try {
                try {
                    if (!lock.isHeldByCurrentThread()) {
                        throw new IllegalStateException("任务结束时已失去分布式锁所有权: " + lockName);
                    }
                    lock.unlock();
                } catch (RuntimeException | Error releaseFailure) {
                    if (taskFailure == null) {
                        throw releaseFailure;
                    }
                    taskFailure.addSuppressed(releaseFailure);
                }
            } finally {
                currentLocks.remove(lockName);
                if (currentLocks.isEmpty()) {
                    heldLocks.remove();
                }
            }
        }
    }

    /**
     * 取得锁后在调用线程同步创建任务的 CompletionStage，并在它完成后释放锁。
     *
     * <p>获取锁时会阻塞调用线程直到取得锁或超过 waitTime。每次调用使用新的逻辑 owner ID，
     * 不使用线程内重入记录；同一线程已经持有同名锁时，内层调用同样需要等待，超时后返回 CONFLICT。
     * 等待超时时返回以 CONFLICT 失败的 CompletionStage，获取锁过程中的异常以其原因失败，任务都不会执行。
     * 任务的 CompletionStage 完成后解锁：
     * 任务成功且解锁成功时以任务结果完成；任务失败或解锁失败时以异常完成。
     *
     * @param key 锁键
     * @param waitTime 最大等待时间；必须大于等于零
     * @param task 取得锁后同步调用、且必须返回非 null CompletionStage 的任务
     * @param <T> 任务结果类型
     * @return 锁内任务的异步结果；等待失败时为已失败的结果
     * @throws IllegalArgumentException 等待时间为负数时抛出
     * @throws NullPointerException 锁键、等待时间或任务为空时抛出
     */
    @Override
    public <T> CompletionStage<T> executeAsync(
            @NotNull LockKey key,
            @NotNull Duration waitTime,
            @NotNull Supplier<? extends CompletionStage<T>> task) {
        LockKey checkedKey = Objects.requireNonNull(key, "锁键不能为空");
        Duration checkedWait = Objects.requireNonNull(waitTime, "锁等待时间不能为空");
        Supplier<? extends CompletionStage<T>> checkedTask = Objects.requireNonNull(task, "锁内任务不能为空");
        if (checkedWait.isNegative()) {
            throw new IllegalArgumentException("锁等待时间不能为负数");
        }
        long ownerId = ASYNC_OWNERS.getAndIncrement();
        RLock lock = redisson.getLock(checkedKey.format(COLON));
        // 锁获取阻塞当前线程，取得锁后才在调用线程同步调用任务；因此任务创建 CompletionStage 时仍可读取当前 Call 和事务状态。
        boolean acquired;
        try {
            acquired = lock.tryLockAsync(checkedWait.toMillis(), -1, TimeUnit.MILLISECONDS, ownerId)
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException failure) {
            return CompletableFuture.failedFuture(unwrap(failure));
        }
        if (!acquired) {
            return CompletableFuture.failedFuture(new BusinessException(CoreErrorCode.CONFLICT));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletionStage<T> current;
        try {
            current = Objects.requireNonNull(checkedTask.get(), "异步锁内任务不能返回 null");
        } catch (Throwable targetFailure) {
            releaseAsync(lock, ownerId, null, targetFailure, result);
            return result;
        }
        current.whenComplete((value, targetFailure) -> releaseAsync(lock, ownerId, value, targetFailure, result));
        return result;
    }

    /**
     * 按逻辑 owner ID 解锁，并在解锁完成后以任务结果或失败完成 result。
     *
     * <p>任务失败时保留任务异常为主异常，解锁异常作为 suppressed 附加；任务成功但解锁失败时以解锁异常完成。
     *
     * @param lock 已取得的 Redis 锁
     * @param ownerId 取得锁时使用的逻辑 owner ID
     * @param value 任务成功时的结果
     * @param targetFailure 任务失败原因，成功时为 null
     * @param result 需要被完成的外部结果
     */
    private static <T> void releaseAsync(
            RLock lock,
            long ownerId,
            T value,
            Throwable targetFailure,
            CompletableFuture<T> result) {
        CompletionStage<Void> unlock;
        try {
            unlock = lock.unlockAsync(ownerId);
        } catch (Throwable unlockFailure) {
            result.completeExceptionally(combine(targetFailure, unlockFailure));
            return;
        }
        unlock.whenComplete((ignored, unlockFailure) -> {
            Throwable failure = unlockFailure == null ? null : unwrap(unlockFailure);
            if (targetFailure != null || failure != null) {
                result.completeExceptionally(combine(targetFailure, failure));
            } else {
                result.complete(value);
            }
        });
    }

    /**
     * 选出异步结果的主异常，并把另一个异常附加为 suppressed。
     *
     * @param first 任务失败原因，可为 null
     * @param second 解锁失败原因，可为 null
     * @return 主异常；两者都缺失时返回 IllegalStateException
     */
    private static Throwable combine(Throwable first, Throwable second) {
        Throwable primary = first == null ? second : unwrap(first);
        if (primary == null) {
            return new IllegalStateException("异步锁失败原因缺失");
        }
        if (second != null && primary != second) {
            primary.addSuppressed(second);
        }
        return primary;
    }

    /**
     * 去掉 CompletionException 包装，返回真正的失败原因。
     *
     * @param failure 异步处理过程中捕获的异常
     * @return 包装异常的原因；没有包装时返回原异常
     */
    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
    }
}
