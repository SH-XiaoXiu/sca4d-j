package com.sca4d.job.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobDispatch;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunRecording;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobSubmission;
import com.sca4d.core.api.job.JobTarget;
import com.sca4d.core.api.job.JobWorkKey;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryDecision;
import com.sca4d.core.api.retry.RetryPolicy;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.runtime.InstanceId;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.job.exception.JobException;
import com.sca4d.job.persistence.ExpiredJobAttempt;
import com.sca4d.job.persistence.JobDelivery;
import com.sca4d.job.persistence.JobInstance;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 创建 Job run、广播和无记录份额，领取与续租节点，保存节点和根运行结果；每个动作在独立事务中完成。 */
public class JobRunService {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull JobRepository repository;

    private final @NotNull Sca4djMapper objectMapper;

    private final @NotNull Clock clock;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /** 创建使用脚手架 JSON 映射器的 JobRunService。 */
    public JobRunService(
            @NotNull JobRepository repository,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull Clock clock,
            @NotNull DistributedLock distributedLock,
            @NotNull Sca4djMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "Job Repository 不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "Job 事务管理器不能为空"));
        this.clock = Objects.requireNonNull(clock, "Job 时钟不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "Job 分布式锁不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Job JSON 映射器不能为空");
    }

    /** 按工作目标提交一次普通运行。 */
    public @NotNull JobSubmission create(
            @NotNull Job<?, ?> job,
            @Nullable String inputJson,
            @NotNull TreeCode systemIdentity,
            @NotNull JobWorkKey workKey,
            @NotNull OverlapPolicy overlapPolicy,
            @NotNull JobTarget target) {
        var checked = Objects.requireNonNull(job, "Job 不能为空");
        if (target.kind() == JobTarget.Kind.ALL) {
            throw new IllegalArgumentException("广播 Job 需使用 broadcast 入口");
        }
        InstanceId targetInstanceId = target.instanceId();
        Duration waitForInstance = target.waitForInstance();
        Instant targetExpiresAt = waitForInstance == null ? null : clock.instant().plus(waitForInstance);
        return distributedLock.execute(
                workLock(checked, systemIdentity, workKey),
                LOCK_WAIT,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    RunRecord active = overlapPolicy == OverlapPolicy.SKIP_IF_RUNNING
                            ? repository.findActiveWork(systemIdentity, checked.key(), workKey, targetInstanceId)
                            : null;
                    if (active != null) {
                        return active.jobDefinitionVersion() == checked.definitionVersion()
                                && Objects.equals(active.inputJson(), inputJson)
                                        ? JobSubmission.existing(Id.of(active.id()))
                                        : JobSubmission.skipped();
                    }
                    return JobSubmission.created(
                            repository.createRun(
                                    checked,
                                    inputJson,
                                    null,
                                    null,
                                    clock.instant(),
                                    systemIdentity,
                                    workKey,
                                    overlapPolicy,
                                    targetInstanceId,
                                    null,
                                    targetExpiresAt,
                                    null));
                })));
    }

    /** 按原定触发和目标实例共同限定定时运行。 */
    public @NotNull Optional<Id> createScheduled(
            @NotNull Job<?, ?> job,
            @Nullable String inputJson,
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @NotNull OverlapPolicy overlapPolicy,
            @NotNull TreeCode systemIdentity,
            @NotNull JobWorkKey workKey,
            @NotNull JobTarget target) {
        if (target.kind() == JobTarget.Kind.ALL) {
            throw new IllegalArgumentException("广播定时 Job 需使用 broadcast 入口");
        }
        ScheduleKey key = Objects.requireNonNull(scheduleKey, "ScheduleKey 不能为空");
        InstanceId targetInstanceId = target.instanceId();
        Duration waitForInstance = target.waitForInstance();
        Instant targetExpiresAt = waitForInstance == null ? null : clock.instant().plus(waitForInstance);
        return distributedLock.execute(
                workLock(job, systemIdentity, workKey),
                LOCK_WAIT,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    Long existing = repository.findScheduledRun(
                            key,
                            Objects.requireNonNull(scheduledAt, "原定时间不能为空"),
                            targetInstanceId);
                    if (existing != null) {
                        return Optional.of(Id.of(existing));
                    }
                    if (Objects.requireNonNull(overlapPolicy, "重叠策略不能为空") == OverlapPolicy.SKIP_IF_RUNNING
                            && repository
                                    .findActiveWork(systemIdentity, job.key(), workKey, targetInstanceId) != null) {
                        return Optional.empty();
                    }
                    return Optional.of(
                            repository.createRun(
                                    job,
                                    inputJson,
                                    key,
                                    scheduledAt,
                                    clock.instant(),
                                    systemIdentity,
                                    workKey,
                                    overlapPolicy,
                                    targetInstanceId,
                                    null,
                                    targetExpiresAt,
                                    null));
                })));
    }

    /** 登记当前实例启用的 capability。 */
    public void registerInstance(
            @NotNull InstanceId instanceId,
            @NotNull UUID generationId,
            @NotNull String capabilitiesJson,
            @NotNull Duration leaseDuration) {
        transactions.executeWithoutResult(
                status -> repository.registerInstance(
                        instanceId,
                        generationId,
                        capabilitiesJson,
                        clock.instant().plus(leaseDuration)));
    }

    /** 查询在线且启用目标 capability 的实例。 */
    public @NotNull List<JobInstance> eligibleInstances(@NotNull String capabilityCode) {
        return Objects.requireNonNull(
                transactions.execute(
                        status -> repository.eligibleInstances(capabilityCode, clock.instant())));
    }

    /** 原子固定全部广播目标，并按 Job 记录策略创建各目标份额。 */
    public @NotNull JobDispatch dispatch(
            @NotNull Job<?, ?> job,
            @Nullable String inputJson,
            @NotNull TreeCode systemIdentity,
            @NotNull JobWorkKey workKey,
            @NotNull OverlapPolicy overlapPolicy,
            @NotNull JobTarget target,
            @Nullable ScheduleKey scheduleKey,
            @Nullable Instant scheduledAt) {
        if (target.kind() == JobTarget.Kind.ANY) {
            throw new IllegalArgumentException("任一实例执行无需创建目标广播");
        }
        if (job.runRecording() == JobRunRecording.NONE && overlapPolicy == OverlapPolicy.WAIT) {
            throw new IllegalArgumentException("无记录 Job 不能等待持久化执行队列: " + job.key());
        }
        if ((scheduleKey == null) != (scheduledAt == null)) {
            throw new IllegalArgumentException("定时广播计划和原定时间必须同时存在");
        }
        return distributedLock.execute(
                workLock(job, systemIdentity, workKey),
                LOCK_WAIT,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    if (scheduleKey != null) {
                        var existing = repository.findScheduledDispatch(scheduleKey, scheduledAt);
                        if (existing != null) {
                            return dispatchResult(existing.id(), existing.targetsJson());
                        }
                    }
                    List<JobInstance> targets = target.kind() == JobTarget.Kind.INSTANCE
                            ? List.of(
                                    new JobInstance(
                                            Objects.requireNonNull(target.instanceId())
                                                    .format(
                                                            NameFormat.COLON),
                                            null))
                            : repository.eligibleInstances(
                                    job.requiredCapability().format(NameFormat.COLON),
                                    clock.instant());
                    if (targets.isEmpty()) {
                        throw new IllegalStateException("广播没有在线且具备 Job 能力的目标实例: " + job.key());
                    }
                    Map<String, UUID> snapshot = new LinkedHashMap<>();
                    targets.forEach(
                            instance -> snapshot.put(
                                    instance.instanceId().format(NameFormat.COLON),
                                    instance.generationId()));
                    Id dispatchId = repository.createDispatch(job.key(), scheduleKey, scheduledAt, json(snapshot));
                    Instant now = clock.instant();
                    Duration waitForInstance = Objects.requireNonNull(
                            target.waitForInstance(),
                            "固定或广播目标缺少等待时间");
                    Instant expiresAt = now.plus(waitForInstance);
                    Map<InstanceId, Id> runs = new LinkedHashMap<>();
                    List<JobInstance> deliveryTargets = new ArrayList<>();
                    for (JobInstance instance : targets) {
                        InstanceId instanceId = instance.instanceId();
                        if (overlapPolicy == OverlapPolicy.SKIP_IF_RUNNING
                                && (repository.findActiveWork(systemIdentity, job.key(), workKey, instanceId) != null
                                        || repository
                                                .hasActiveDelivery(systemIdentity, job.key(), workKey, instanceId))) {
                            continue;
                        }
                        if (job.runRecording() == JobRunRecording.PERSISTED) {
                            Id runId = repository.createRun(
                                    job,
                                    inputJson,
                                    scheduleKey,
                                    scheduledAt,
                                    now,
                                    systemIdentity,
                                    workKey,
                                    overlapPolicy,
                                    instanceId,
                                    instance.generationId(),
                                    expiresAt,
                                    dispatchId.value());
                            runs.put(instanceId, runId);
                        } else {
                            deliveryTargets.add(instance);
                        }
                    }
                    repository.createDeliveries(
                            dispatchId,
                            job,
                            systemIdentity,
                            workKey,
                            overlapPolicy,
                            inputJson,
                            deliveryTargets,
                            expiresAt);
                    return new JobDispatch(
                            dispatchId,
                            new LinkedHashSet<>(
                                    snapshot.keySet()
                                            .stream()
                                            .map(InstanceId::new)
                                            .toList()),
                            runs);
                })));
    }

    /** 从已保存的广播目标快照和各目标运行记录还原 JobDispatch；快照无法解析时抛出 JobException。 */
    private @NotNull JobDispatch dispatchResult(long dispatchId, @NotNull String targetsJson) {
        Map<String, UUID> snapshot;
        try {
            snapshot = objectMapper.readValue(targetsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new JobException("广播目标快照无法恢复: " + dispatchId, exception);
        }
        Id id = Id.of(dispatchId);
        Map<InstanceId, Id> runs = new LinkedHashMap<>();
        repository.dispatchRuns(id).forEach(item -> runs.put(item.instanceId(), Id.of(item.runId())));
        return new JobDispatch(
                id,
                new LinkedHashSet<>(snapshot.keySet().stream().map(InstanceId::new).toList()),
                runs);
    }

    /** 把广播目标快照序列化为 JSON 文本；序列化失败时抛出 IllegalArgumentException。 */
    private @NotNull String json(@NotNull Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Job 广播目标无法序列化", exception);
        }
    }

    /** 领取一份目标实例的无记录工作。 */
    public @Nullable JobDelivery claimDelivery(
            @NotNull InstanceId instanceId,
            @NotNull UUID generationId,
            @NotNull Duration leaseDuration,
            @NotNull Map<JobKey, Map<Integer, InstanceCapability>> definitions) {
        Instant now = clock.instant();
        return transactions.execute(
                status -> repository.claimDelivery(
                        instanceId,
                        generationId,
                        now,
                        now.plus(leaseDuration),
                        definitions));
    }

    /** 续期一个正在执行的无记录份额。 */
    public boolean renewDelivery(
            @NotNull JobDelivery delivery,
            @NotNull UUID generationId,
            @NotNull Duration leaseDuration) {
        Instant now = clock.instant();
        return Boolean.TRUE.equals(
                transactions.execute(
                        status -> repository.renewDelivery(
                                delivery,
                                generationId,
                                now,
                                now.plus(leaseDuration))));
    }

    /** 完成一份无记录工作；当前代次失去份额时不覆盖结果。 */
    public boolean completeDelivery(
            @NotNull JobDelivery delivery,
            @NotNull UUID generationId,
            boolean succeeded,
            @Nullable String error) {
        return Boolean.TRUE.equals(
                transactions.execute(
                        status -> repository.completeDelivery(
                                delivery,
                                generationId,
                                succeeded,
                                error,
                                clock.instant())));
    }

    /** 结束超时工作并删除已结束的短期协调记录。 */
    public void maintainDeliveries() {
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            repository.maintainDeliveries(now, now.minus(Duration.ofHours(1)));
        });
    }

    /** 注销当前进程代次。 */
    public void unregisterInstance(@NotNull InstanceId instanceId, @NotNull UUID generationId) {
        transactions.executeWithoutResult(status -> repository.unregisterInstance(instanceId, generationId));
    }

    /** 在应用隔离名称内串行处理同一系统身份、Job 和工作键的提交。 */
    static @NotNull LockKey workLock(
            @NotNull Job<?, ?> job,
            @NotNull TreeCode systemIdentity,
            @NotNull JobWorkKey workKey) {
        return new LockKey(
                TreeCode.ofColon("JOB:WORK"),
                systemIdentity.format(NameFormat.COLON),
                job.key().format(NameFormat.COLON),
                workKey.value());
    }

    /** 查询当前 App 隔离名称的根运行。 */
    public @Nullable RunRecord findRun(@NotNull Id runId) {
        return transactions.execute(status -> repository.findRun(runId));
    }

    /** 按当前实例及启动代次领取可执行节点。 */
    public @Nullable NodeClaim claim(
            @NotNull String workerId,
            @NotNull InstanceId instanceId,
            @NotNull UUID generationId,
            @NotNull Duration leaseDuration,
            @NotNull Map<JobKey, Map<Integer, InstanceCapability>> definitions) {
        Instant now = clock.instant();
        return transactions
                .execute(
                        status -> repository
                                .claim(
                                        workerId,
                                        instanceId,
                                        generationId,
                                        now,
                                        now.plus(Objects.requireNonNull(leaseDuration)),
                                        definitions));
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
            repository.lockRun(Id.of(claim.runId()));
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
    public @NotNull RetryDecision fail(@NotNull NodeClaim claim, @NotNull String workerId, @NotNull String error) {
        return Objects.requireNonNull(transactions.execute(status -> {
            Instant failedAt = clock.instant();
            RetryDecision decision = RetryPolicy.always(claim.retryRule())
                    .evaluate(RetryContext.failed(claim.attempt(), error));
            if (decision.retrying()) {
                repository.saveNodeResult(
                        claim.retry(error, failedAt.plus(decision.retryDelay().orElseThrow()), failedAt),
                        workerId);
            } else {
                repository.failNode(claim.nodeId(), claim.runId(), claim.attempt(), workerId, error, failedAt);
            }
            return decision;
        }));
    }

    /** 放弃过期尝试，按冻结规则结束或重新排队，并结束超过等待期限的目标。 */
    public void recoverExpiredAttempts() {
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            repository.failExpiredExhausted(now);
            for (ExpiredJobAttempt attempt : repository.expiredAttempts(now)) {
                RetryDecision decision = RetryPolicy.always(attempt.retryRule())
                        .evaluate(RetryContext.failed(attempt.attempt(), "LEASE_EXPIRED"));
                repository.rescheduleExpired(
                        attempt,
                        attempt.leaseUntil().plus(decision.retryDelay().orElseThrow()),
                        now);
            }
            repository.failExpiredTargets(now);
        });
    }

}
