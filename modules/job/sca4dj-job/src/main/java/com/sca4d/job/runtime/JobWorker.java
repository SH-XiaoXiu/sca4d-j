package com.sca4d.job.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.JobRunRecording;
import com.sca4d.core.api.job.ParallelResults;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceId;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.job.config.JobProperties;
import com.sca4d.job.persistence.JobDelivery;
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

/** 轮询可执行节点和短期投递，在有界线程池中执行并为运行中的工作续租。 */
public final class JobWorker implements SmartLifecycle {

    private static final int MAX_ERROR_LENGTH = 1000;

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final @NotNull JobRunService runs;

    private final @NotNull Sca4djMapper objectMapper;

    private final @NotNull JobProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull JobCatalog catalog;

    private final @NotNull Name applicationId;

    private final @NotNull InstanceProperties instanceProperties;

    private final @NotNull InstanceCapabilities instanceCapabilities;

    private final @NotNull UUID workerId;

    private final @NotNull UUID generationId;

    private final @NotNull ExecutorService workers;

    private final @NotNull InstanceId instanceId;

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
     * @param instanceProperties 当前进程的实例标识配置
     * @param instanceCapabilities 当前实例启用的能力
     */
    public JobWorker(
            @NotNull JobCatalog catalog,
            @NotNull JobRunService runs,
            @NotNull Sca4djMapper objectMapper,
            @NotNull JobProperties properties,
            @NotNull Clock clock,
            @NotNull InstanceProperties instanceProperties,
            @NotNull InstanceCapabilities instanceCapabilities) {
        this.catalog = Objects.requireNonNull(catalog, "JobCatalog 不能为空");
        this.runs = Objects.requireNonNull(runs, "JobRunService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Job ObjectMapper 不能为空");
        this.properties = Objects.requireNonNull(properties, "JobProperties 不能为空");
        this.clock = Objects.requireNonNull(clock, "Job 时钟不能为空");
        this.applicationId = AppName.current();
        this.instanceProperties = Objects.requireNonNull(instanceProperties, "实例配置不能为空");
        this.instanceCapabilities = Objects.requireNonNull(instanceCapabilities, "实例能力不能为空");
        this.instanceId = new InstanceId(instanceProperties.getId());
        this.workerId = UUID.randomUUID();
        this.generationId = UUID.randomUUID();
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
        registerInstanceSafely();
        coordinator.scheduleWithFixedDelay(
                this::registerInstanceSafely,
                properties.getLeaseRenewInterval().toMillis(),
                properties.getLeaseRenewInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        coordinator.scheduleWithFixedDelay(
                this::pollSafely,
                0,
                properties.getPollInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        coordinator.scheduleWithFixedDelay(
                this::maintainDeliveriesSafely,
                TimeUnit.MINUTES.toMillis(1),
                TimeUnit.MINUTES.toMillis(1),
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
        try {
            runs.unregisterInstance(instanceId, generationId);
        } catch (RuntimeException exception) {
            log.warn("Job 实例登记未能注销，租约到期后失效 | instanceId={}", instanceProperties.getId(), exception);
        }
        log.info("Job worker 已停止 | applicationId={} workerId={}", applicationId, workerId);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 200;
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
                NodeClaim claim = runs.claim(
                        workerId.toString(),
                        instanceId,
                        generationId,
                        properties.getLeaseDuration(),
                        catalog.availableVersions(instanceCapabilities));
                if (claim == null) {
                    break;
                }
                activeWorkers.incrementAndGet();
                workers.execute(() -> executeClaim(claim));
            }
            capacity = properties.getWorkerCount() - activeWorkers.get();
            for (int index = 0; index < Math.min(capacity, properties.getClaimBatchSize()); index++) {
                JobDelivery delivery = runs.claimDelivery(
                        instanceId,
                        generationId,
                        properties.getLeaseDuration(),
                        catalog.availableVersions(instanceCapabilities));
                if (delivery == null) {
                    break;
                }
                activeWorkers.incrementAndGet();
                workers.execute(() -> executeDelivery(delivery));
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

    /** 结束已过期份额并删除到期的短期协调数据。 */
    private void maintainDeliveriesSafely() {
        if (!running.get()) {
            return;
        }
        try {
            runs.maintainDeliveries();
        } catch (RuntimeException exception) {
            log.warn("Job 短期投递维护失败，下轮重试 | instanceId={}", instanceId, exception);
        }
    }

    /** 目标实例只执行一遍无记录份额，最终结果不生成 Job Run。 */
    private void executeDelivery(@NotNull JobDelivery delivery) {
        Instant startedAt = clock.instant();
        ScheduledFuture<?> heartbeat = null;
        UUID executionId = UUID.randomUUID();
        try {
            heartbeat = coordinator.scheduleAtFixedRate(
                    () -> renewDeliverySafely(delivery),
                    properties.getLeaseRenewInterval().toMillis(),
                    properties.getLeaseRenewInterval().toMillis(),
                    TimeUnit.MILLISECONDS);
            Job<?, ?> job = catalog.loadByKeyAndVersion(delivery.jobKey(), delivery.jobDefinitionVersion());
            if (!(job instanceof DirectJob<?, ?> directJob)
                    || job.runRecording() != JobRunRecording.NONE) {
                throw new IllegalStateException("短期投递需要无记录 DirectJob: " + job.key());
            }
            Object input = deserialize(delivery.inputJson(), directJob.inputType());
            SystemPrincipal principal = systemPrincipal(job);
            Object output = CallHolder.run(
                    Call.system(executionId.toString(), startedAt, principal),
                    () -> runDirect(
                            directJob,
                            new JobExecution(
                                    executionId,
                                    null,
                                    1,
                                    startedAt,
                                    instanceId,
                                    job.requiredCapability(),
                                    principal),
                            input));
            if (output == null && !Void.class.equals(directJob.outputType())
                    || output != null && !directJob.outputType().isInstance(output)) {
                throw new IllegalStateException("无记录 Job 输出类型不一致: " + job.key());
            }
            if (!runs.completeDelivery(delivery, generationId, true, null)) {
                log.warn("无记录 Job 已执行，但投递所有权已失效 | deliveryId={}", delivery.id());
            }
        } catch (Exception exception) {
            String error = errorSummary(
                    exception instanceof RuntimeException runtime
                            ? runtime
                            : new IllegalStateException(exception));
            try {
                runs.completeDelivery(delivery, generationId, false, error);
            } catch (RuntimeException persistenceFailure) {
                log.warn("无记录 Job 失败结果未写回 | deliveryId={}", delivery.id(), persistenceFailure);
            }
            log.warn(
                    "无记录 Job 执行失败 | jobKey={} deliveryId={} instanceId={}",
                    delivery.jobKey(),
                    delivery.id(),
                    instanceId,
                    exception);
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            activeWorkers.decrementAndGet();
        }
    }

    private void renewDeliverySafely(@NotNull JobDelivery delivery) {
        try {
            if (!runs.renewDelivery(delivery, generationId, properties.getLeaseDuration())) {
                log.warn("无记录 Job 份额租约已失效 | deliveryId={} instanceId={}", delivery.id(), instanceId);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "无记录 Job 份额续租失败，下轮重试 | deliveryId={} instanceId={}",
                    delivery.id(),
                    instanceId,
                    exception);
        }
    }

    /** 定期发布当前进程代次和启用能力，供固定目标和广播选择。 */
    private void registerInstanceSafely() {
        if (!running.get()) {
            return;
        }
        try {
            String capabilitiesJson = objectMapper.writeValueAsString(
                    instanceCapabilities.enabledCodes()
                            .stream()
                            .map(code -> code.format(com.sca4d.core.api.name.NameFormat.COLON))
                            .sorted()
                            .toList());
            runs.registerInstance(
                    instanceId,
                    generationId,
                    capabilitiesJson,
                    properties.getLeaseDuration());
        } catch (Exception exception) {
            log.warn("Job 实例登记失败，下次续期将重试 | instanceId={}", instanceProperties.getId(), exception);
        }
    }

    /** 执行一个已经领取的节点，并在结束后写回成功或失败。 */
    private void executeClaim(@NotNull NodeClaim claim) {
        ScheduledFuture<?> heartbeat = null;
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
            // 业务动作执行期间定期续租，其他实例只能在租约过期后重新领取该节点。
            heartbeat = coordinator.scheduleAtFixedRate(
                    () -> renewSafely(claim),
                    properties.getLeaseRenewInterval().toMillis(),
                    properties.getLeaseRenewInterval().toMillis(),
                    TimeUnit.MILLISECONDS);
            // 根据持久化 key 和定义版本恢复定义，执行完整 DirectJob 或 Pipeline 节点后写回结果。
            Object output = executeWithSystemCall(claim, startedAt);
            String outputJson = serializeOutput(output);
            runs.succeed(claim, workerId.toString(), outputJson);
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
                runs.fail(claim, workerId.toString(), error);
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
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            activeWorkers.decrementAndGet();
        }
    }

    /** 执行 DirectJob 或 PipelineJob 中的一个 Pipeline 节点。 */
    private @Nullable Object execute(
            @NotNull NodeClaim claim,
            @NotNull Instant startedAt,
            @NotNull UUID executionId) {
        Job<?, ?> job = catalog.loadByKeyAndVersion(claim.jobKey(), claim.jobDefinitionVersion());
        if (job instanceof DirectJob<?, ?> directJob) {
            if (!"DIRECT".equals(claim.jobKind())) {
                throw new IllegalStateException("Job run kind 与 DirectJob 不一致: " + job.key());
            }
            Object input = deserialize(claim.inputJson(), directJob.inputType());
            Object output = runDirect(
                    directJob,
                    new JobExecution(
                            executionId,
                            Id.of(claim.runId()),
                            claim.attempt(),
                            startedAt,
                            instanceId,
                            job.requiredCapability(),
                            systemPrincipal(job)),
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

    /** 在由 Job 能力选择的系统身份中执行一次节点。 */
    private @Nullable Object executeWithSystemCall(
            @NotNull NodeClaim claim,
            @NotNull Instant startedAt) {
        Job<?, ?> job = catalog.loadByKeyAndVersion(claim.jobKey(), claim.jobDefinitionVersion());
        UUID executionId = UUID.randomUUID();
        Call call = Call.system(executionId.toString(), startedAt, systemPrincipal(job));
        try {
            return CallHolder.run(call, () -> execute(claim, startedAt, executionId));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Job 系统调用执行失败: " + job.key(), exception);
        }
    }

    /** 从当前实例能力目录解析 Job 使用的稳定系统身份。 */
    private @NotNull SystemPrincipal systemPrincipal(@NotNull Job<?, ?> job) {
        var capability = instanceCapabilities.definitions().get(job.requiredCapability());
        if (capability == null || !instanceCapabilities.enabled(job.requiredCapability())) {
            throw new IllegalStateException("当前实例未启用 Job 所需能力: " + job.requiredCapability());
        }
        return SystemPrincipals.require(capability.systemIdentity());
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
            if (!runs.renew(claim.nodeId(), workerId.toString(), properties.getLeaseDuration())) {
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
