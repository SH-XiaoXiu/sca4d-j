package com.sca4d.job.runtime;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunId;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.job.persistence.JobRepository;
import com.sca4d.job.persistence.NodeClaim;
import com.sca4d.job.persistence.RunRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 编排 Job 创建、租约和状态转换，并为每个完整动作设置事务边界。 */
public final class JobRunService {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull JobRepository repository;

    private final @NotNull TransactionTemplate transactions;

    private final @NotNull Clock clock;

    private final @NotNull DistributedLock distributedLock;

    /** 创建使用指定 Job Repository 和时钟的运行服务。 */
    public JobRunService(
            @NotNull JobRepository repository,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull Clock clock,
            @NotNull DistributedLock distributedLock) {
        this.repository = Objects.requireNonNull(repository, "Job Repository 不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "Job 事务管理器不能为空"));
        this.clock = Objects.requireNonNull(clock, "Job 时钟不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "Job 分布式锁不能为空");
    }

    /** 创建普通 Job run，并在同一事务展开节点和依赖。 */
    public @NotNull JobRunId create(@NotNull Job<?, ?> job, @Nullable String inputJson) {
        return Objects.requireNonNull(
                transactions.execute(status -> repository.createRun(job, inputJson, null, null, clock.instant())));
    }

    /** 处理定时 fire 的去重和重叠策略，并创建或返回根运行。 */
    public @NotNull Optional<JobRunId> createScheduled(
            @NotNull Job<?, ?> job,
            @Nullable String inputJson,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy) {
        ScheduleKey key = Objects.requireNonNull(scheduleKey, "ScheduleKey 不能为空");
        return distributedLock.execute(
                new LockKey(TreeCode.of("JOB:SCHEDULE").append(key)),
                LOCK_WAIT,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    Long existing = repository.findScheduledRun(
                            key,
                            Objects.requireNonNull(scheduledAt, "原定时间不能为空"));
                    if (existing != null) {
                        return Optional.of(new JobRunId(existing));
                    }
                    if (Objects.requireNonNull(overlapPolicy, "重叠策略不能为空") == OverlapPolicy.SKIP_IF_RUNNING
                            && repository.findActiveScheduledRun(key) != null) {
                        return Optional.empty();
                    }
                    return Optional.of(repository.createRun(job, inputJson, key, scheduledAt, clock.instant()));
                })));
    }

    /** 查询当前 App 隔离名称的根运行。 */
    public @Nullable RunRecord findRun(@NotNull JobRunId runId) {
        return transactions.execute(status -> repository.findRun(runId));
    }

    /** 原子领取一个节点。 */
    public @Nullable NodeClaim claim(
            @NotNull String workerId,
            @NotNull Duration leaseDuration,
            @NotNull Map<JobKey, Integer> definitions) {
        Instant now = clock.instant();
        return transactions
                .execute(
                        status -> repository
                                .claim(workerId, now, now.plus(Objects.requireNonNull(leaseDuration)), definitions));
    }

    /** 续租一个节点。 */
    public boolean renew(long nodeId, @NotNull String workerId, @NotNull Duration leaseDuration) {
        Instant leaseUntil = clock.instant().plus(Objects.requireNonNull(leaseDuration));
        return Boolean.TRUE.equals(transactions.execute(status -> repository.renew(nodeId, workerId, leaseUntil)));
    }

    /** 读取节点依赖输出。 */
    public @NotNull List<@Nullable String> dependencyOutputs(long nodeId) {
        return Objects.requireNonNull(transactions.execute(status -> repository.dependencyOutputs(nodeId)));
    }

    /** 原子保存节点成功，并推进后继或根运行。 */
    public void succeed(@NotNull NodeClaim claim, @NotNull String workerId, @Nullable String outputJson) {
        transactions.executeWithoutResult(status -> {
            Instant completedAt = clock.instant();
            repository.saveNodeResult(claim.succeeded(outputJson, completedAt), workerId);
            if (repository.hasDependents(claim.nodeId())) {
                repository.activateDependents(claim.nodeId(), completedAt);
            } else {
                repository.saveRunResult(claim.runId(), JobRunStatus.SUCCEEDED, outputJson, completedAt);
            }
        });
    }

    /** 原子保存节点失败，并按尝试次数决定重试或结束根运行。 */
    public void fail(@NotNull NodeClaim claim, @NotNull String workerId, @NotNull String error) {
        transactions.executeWithoutResult(status -> {
            Instant failedAt = clock.instant();
            if (claim.attempt() < claim.maxAttempts()) {
                repository.saveNodeResult(
                        claim.retry(error, failedAt.plusMillis(claim.retryDelayMillis()), failedAt),
                        workerId);
            } else {
                repository.failNode(claim.nodeId(), claim.runId(), claim.attempt(), workerId, error, failedAt);
            }
        });
    }

    /** 标记租约到期且重试耗尽的节点。 */
    public void failExpiredExhausted() {
        transactions.executeWithoutResult(status -> repository.failExpiredExhausted(clock.instant()));
    }

}
