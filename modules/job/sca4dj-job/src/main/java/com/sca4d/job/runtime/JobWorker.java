package com.sca4d.job.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.ParallelResults;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.job.config.JobProperties;
import com.sca4d.job.persistence.NodeClaim;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 轮询可执行节点，在有界线程池中执行并为每个运行中的节点续租。 */
public final class JobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private static final int MAX_ERROR_LENGTH = 1000;

    private final @NotNull JobCatalog catalog;

    private final @NotNull JobRunService runs;

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull JobProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull Name applicationId;

    private final @NotNull String workerId;

    private final @NotNull ExecutorService workers;

    private final @NotNull ScheduledExecutorService coordinator;

    private final @NotNull AtomicBoolean running = new AtomicBoolean();

    private final @NotNull AtomicInteger activeWorkers = new AtomicInteger();

    /**
     * 创建在当前 App 隔离名称内领取和执行节点的 worker。
     *
     * @param catalog 当前应用登记的 Job 定义
     * @param runs Job 运行事务服务
     * @param objectMapper 脚手架统一 JSON 映射器
     * @param properties worker、轮询和租约配置
     * @param clock 执行事实使用的时钟
     */
    public JobWorker(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull ObjectMapper objectMapper,
            @NotNull JobProperties properties,
            @NotNull Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "JobCatalog 不能为空");
        this.runs = Objects.requireNonNull(runs, "JobRunService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Job ObjectMapper 不能为空");
        this.properties = Objects.requireNonNull(properties, "JobProperties 不能为空");
        this.clock = Objects.requireNonNull(clock, "Job 时钟不能为空");
        this.applicationId = AppName.current();
        this.workerId = UUID.randomUUID().toString();
        this.workers = Executors.newFixedThreadPool(
                properties.getWorkerCount(),
                Thread.ofPlatform().name("sca4dj-job-worker-", 0).factory());
        this.coordinator = Executors.newScheduledThreadPool(
                2,
                Thread.ofPlatform().name("sca4dj-job-coordinator-", 0).factory());
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        coordinator.scheduleWithFixedDelay(
                this::pollSafely,
                0,
                properties.getPollInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        log.info(
                "Job worker 已启动 | applicationId={} workerId={} workerCount={}",
                applicationId,
                workerId,
                properties.getWorkerCount());
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        coordinator.shutdownNow();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(properties.getLeaseDuration().toMillis(), TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
        log.info("Job worker 已停止 | applicationId={} workerId={}", applicationId, workerId);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** 清理耗尽租约的节点，并在容量范围内领取一批节点。 */
    private void pollSafely() {
        if (!running.get()) {
            return;
        }
        try {
            runs.failExpiredExhausted();
            int capacity = properties.getWorkerCount() - activeWorkers.get();
            int claimCount = Math.min(capacity, properties.getClaimBatchSize());
            for (int index = 0; index < claimCount; index++) {
                NodeClaim claim = runs.claim(workerId, properties.getLeaseDuration(), catalog.definitionVersions());
                if (claim == null) {
                    break;
                }
                activeWorkers.incrementAndGet();
                workers.execute(() -> executeClaim(claim));
            }
        } catch (RuntimeException exception) {
            if (!running.get()) {
                return;
            }
            log.warn(
                    "Job 节点轮询失败，下一轮将重试 | applicationId={} workerId={}",
                    applicationId,
                    workerId,
                    exception);
        }
    }

    /** 执行一个已经领取的节点，并在结束后写回成功或失败。 */
    private void executeClaim(@NotNull NodeClaim claim) {
        // 业务动作执行期间定期续租，其他实例只能在租约过期后重新领取该节点。
        ScheduledFuture<?> heartbeat = coordinator.scheduleAtFixedRate(
                () -> renewSafely(claim),
                properties.getLeaseRenewInterval().toMillis(),
                properties.getLeaseRenewInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        Instant startedAt = clock.instant();
        log.info(
                "Job 节点开始执行 | applicationId={} jobKey={} runId={} nodeKey={} attempt={} workerId={}",
                applicationId,
                claim.jobKey(),
                claim.runId(),
                claim.nodeKey(),
                claim.attempt(),
                workerId);
        try {
            // 根据持久化 key 和定义版本恢复定义，执行完整 DirectJob 或 Pipeline 节点后写回结果。
            Object output = execute(claim, startedAt);
            String outputJson = serializeOutput(output);
            runs.succeed(claim, workerId, outputJson);
            log.info(
                    "Job 节点执行成功 | applicationId={} jobKey={} runId={} nodeKey={} attempt={} workerId={} durationMillis={}",
                    applicationId,
                    claim.jobKey(),
                    claim.runId(),
                    claim.nodeKey(),
                    claim.attempt(),
                    workerId,
                    Duration.between(startedAt, clock.instant()).toMillis());
        } catch (RuntimeException exception) {
            String error = errorSummary(exception);
            try {
                // Job runtime 根据节点策略重新排队或结束根运行；业务异常只在最终处理位置记录一次。
                runs.fail(claim, workerId, error);
                if (claim.attempt() < claim.maxAttempts()) {
                    log.warn(
                            "Job 节点执行失败，等待重试 | applicationId={} jobKey={} runId={} nodeKey={} attempt={} maxAttempts={} workerId={}",
                            applicationId,
                            claim.jobKey(),
                            claim.runId(),
                            claim.nodeKey(),
                            claim.attempt(),
                            claim.maxAttempts(),
                            workerId,
                            exception);
                } else {
                    log.error(
                            "Job 节点重试耗尽 | applicationId={} jobKey={} runId={} nodeKey={} attempt={} workerId={}",
                            applicationId,
                            claim.jobKey(),
                            claim.runId(),
                            claim.nodeKey(),
                            claim.attempt(),
                            workerId,
                            exception);
                }
            } catch (RuntimeException persistenceFailure) {
                log.warn(
                        "Job 节点结果未写回，租约到期后将恢复 | applicationId={} jobKey={} runId={} nodeKey={} attempt={} workerId={}",
                        applicationId,
                        claim.jobKey(),
                        claim.runId(),
                        claim.nodeKey(),
                        claim.attempt(),
                        workerId,
                        persistenceFailure);
            }
        } finally {
            // 节点不再执行后停止续租，并释放本实例的 worker 容量。
            heartbeat.cancel(false);
            activeWorkers.decrementAndGet();
        }
    }

    /** 执行 DirectJob 或 PipelineJob 中的一个 Pipeline 节点。 */
    private @Nullable Object execute(@NotNull NodeClaim claim, @NotNull Instant startedAt) {
        Job<?, ?> job = catalog.loadByKeyAndVersion(claim.jobKey(), claim.jobDefinitionVersion());
        if (job instanceof DirectJob<?, ?> directJob) {
            if (!"DIRECT".equals(claim.jobKind())) {
                throw new IllegalStateException("Job run kind 与 DirectJob 不一致: " + job.key());
            }
            Object input = deserialize(claim.inputJson(), directJob.inputType());
            Object output = runDirect(
                    directJob,
                    new JobExecution(
                            Id.of(claim.runId()),
                            claim.attempt(),
                            startedAt),
                    input);
            if (output == null && !Void.class.equals(directJob.outputType())) {
                throw new IllegalStateException("非 Void DirectJob 返回了 null: " + directJob.key());
            }
            if (output != null && !directJob.outputType().isInstance(output)) {
                throw new IllegalStateException("DirectJob 输出类型不一致: " + directJob.key());
            }
            return output;
        }
        if (job instanceof PipelineJob<?, ?> pipelineJob) {
            if (!"PIPELINE".equals(claim.jobKind())) {
                throw new IllegalStateException("Job run kind 与 PipelineJob 不一致: " + job.key());
            }
            PipelineJobPlan.Node node = node(pipelineJob.definition(), claim.nodeKey());
            Object input = node.parallelMerge()
                    ? parallelInput(claim.nodeId(), pipelineJob.definition(), node)
                    : Objects.requireNonNull(
                            deserialize(claim.inputJson(), node.pipeline().inputType()),
                            "PipelineJob 节点输入不能为空: " + node.key());
            return runPipeline(node.pipeline(), input);
        }
        throw new IllegalStateException("不支持的 Job 子类: " + job.getClass().getName());
    }

    /** 从两个依赖节点的类型和持久化结果恢复合并输入。 */
    private @NotNull ParallelResults<?, ?> parallelInput(
            long nodeId,
            @NotNull PipelineJobPlan<?, ?> plan,
            @NotNull PipelineJobPlan.Node node) {
        List<String> outputs = runs.dependencyOutputs(nodeId);
        if (node.dependencies().size() != 2 || outputs.size() != 2) {
            throw new IllegalStateException("PipelineJob 合并节点必须恰好有两个依赖: " + node.key());
        }
        PipelineJobPlan.Node leftNode = node(plan, node.dependencies().get(0));
        PipelineJobPlan.Node rightNode = node(plan, node.dependencies().get(1));
        Object left = Objects.requireNonNull(
                deserialize(outputs.get(0), leftNode.pipeline().outputType()),
                "PipelineJob 左分支输出不能为空: " + leftNode.key());
        Object right = Objects.requireNonNull(
                deserialize(outputs.get(1), rightNode.pipeline().outputType()),
                "PipelineJob 右分支输出不能为空: " + rightNode.key());
        return new ParallelResults<>(left, right);
    }

    /** 返回计划中稳定名称匹配的节点。 */
    private static @NotNull PipelineJobPlan.Node node(
            @NotNull PipelineJobPlan<?, ?> plan,
            @NotNull String nodeKey) {
        return plan.nodes()
                .stream()
                .filter(node -> node.key().equals(nodeKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PipelineJob 节点定义不存在: " + nodeKey));
    }

    /** 恢复节点输入；Void 输入恢复为 null。 */
    private @Nullable Object deserialize(@Nullable String json, @NotNull Class<?> type) {
        if (Void.class.equals(type)) {
            if (json != null) {
                throw new IllegalStateException("Void Job 节点不应保存输入 JSON");
            }
            return null;
        }
        if (json == null) {
            throw new IllegalStateException("Job 节点输入缺失: " + type.getName());
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Job 节点输入无法恢复: " + type.getName(), exception);
        }
    }

    /** 序列化非空输出；Void DirectJob 可以返回 null。 */
    private @Nullable String serializeOutput(@Nullable Object output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Job 节点输出无法序列化: " + output.getClass().getName(), exception);
        }
    }

    /** 不暴露泛型擦除细节地调用 DirectJob。 */
    @SuppressWarnings("unchecked")
    private static @Nullable Object runDirect(
            @NotNull DirectJob<?, ?> job,
            @NotNull JobExecution execution,
            @Nullable Object input) {
        return ((DirectJob<Object, Object>) job).run(execution, input);
    }

    /** 不暴露泛型擦除细节地调用 Pipeline。 */
    @SuppressWarnings("unchecked")
    private static @NotNull Object runPipeline(
            @NotNull Pipeline<?, ?> pipeline,
            @NotNull Object input) {
        return ((Pipeline<Object, Object>) pipeline).run(input);
    }

    /** 续租失败表示租约已经转移，本次业务动作仍完成但结果写回会被拒绝。 */
    private void renewSafely(@NotNull NodeClaim claim) {
        try {
            if (!runs.renew(claim.nodeId(), workerId, properties.getLeaseDuration())) {
                log.warn(
                        "Job 节点租约已经转移 | applicationId={} jobKey={} runId={} nodeKey={} attempt={} workerId={}",
                        applicationId,
                        claim.jobKey(),
                        claim.runId(),
                        claim.nodeKey(),
                        claim.attempt(),
                        workerId);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Job 节点续租失败，下次续租将重试 | applicationId={} jobKey={} runId={} nodeKey={} attempt={} workerId={}",
                    applicationId,
                    claim.jobKey(),
                    claim.runId(),
                    claim.nodeKey(),
                    claim.attempt(),
                    workerId,
                    exception);
        }
    }

    /** 生成可持久化且长度受限的异常摘要。 */
    private static @NotNull String errorSummary(@NotNull RuntimeException exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getName() + (message == null ? "" : ": " + message);
        return summary.length() <= MAX_ERROR_LENGTH ? summary : summary.substring(0, MAX_ERROR_LENGTH);
    }
}
