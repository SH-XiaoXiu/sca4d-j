package com.sca4d.scheduling.quartz;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.JobDispatch;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.job.JobRunRecording;
import com.sca4d.core.api.job.JobTarget;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceId;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.scheduling.quartz.exception.SchedulingQuartzException;
import org.jetbrains.annotations.NotNull;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 把一次 Quartz fire 转换成有记录提交或无记录系统执行。 */
public class QuartzDispatchJob implements Job {

    /** Quartz SchedulerContext 中保存 InstanceCapabilities 的键。 */
    public static final String CAPABILITIES_CONTEXT_KEY = "sca4djInstanceCapabilities";

    /** Quartz SchedulerContext 中保存 Clock 的键。 */
    public static final String CLOCK_CONTEXT_KEY = "sca4djClock";

    /** Quartz SchedulerContext 中保存 JobExecutor 的键。 */
    public static final String EXECUTOR_CONTEXT_KEY = "sca4djJobExecutor";

    /** Quartz SchedulerContext 中保存 InstanceProperties 的键。 */
    public static final String INSTANCE_CONTEXT_KEY = "sca4djInstanceProperties";

    /** Quartz SchedulerContext 中保存 DistributedLock 的键。 */
    public static final String LOCK_CONTEXT_KEY = "sca4djDistributedLock";

    /** Quartz SchedulerContext 中保存计划目录的键。 */
    public static final String REGISTRY_CONTEXT_KEY = "sca4djScheduleRegistry";

    /** JobDataMap 中保存计划定义版本的键。 */
    public static final String SCHEDULE_DEFINITION_VERSION = "scheduleDefinitionVersion";

    /** JobDataMap 中保存计划 key 的键名。 */
    public static final String SCHEDULE_KEY = "scheduleKey";

    private static final Logger log = LoggerFactory.getLogger(QuartzDispatchJob.class);

    public QuartzDispatchJob() {
    }

    @Override
    public void execute(@NotNull JobExecutionContext context) throws JobExecutionException {
        try {
            // JobDataMap 只保存定位计划所需的标识，实际计划从本实例完成对账后的目录恢复。
            JobDataMap data = context.getMergedJobDataMap();
            ScheduleKey key = new ScheduleKey(data.getString(SCHEDULE_KEY));
            int definitionVersion = Integer.parseInt(data.getString(SCHEDULE_DEFINITION_VERSION));
            QuartzScheduleRegistry registry = (QuartzScheduleRegistry) context.getScheduler()
                    .getContext()
                    .get(REGISTRY_CONTEXT_KEY);
            JobExecutor executor = (JobExecutor) context.getScheduler().getContext().get(EXECUTOR_CONTEXT_KEY);
            JobSchedule<?, ?> schedule = registry.loadByKeyAndVersion(key, definitionVersion);
            Instant scheduledAt = context.getScheduledFireTime().toInstant();
            if (executor == null) {
                if (!quartzCanRunDirectly(schedule)) {
                    throw new IllegalStateException("当前计划需要 JobExecutor: " + key);
                }
                directWithoutJobModule(context, schedule);
                return;
            }
            if (broadcastTarget(schedule)) {
                var dispatch = broadcast(executor, schedule, scheduledAt);
                log.trace(
                        "Quartz fire 已固定广播目标 | scheduleKey={} dispatchId={} targetCount={}",
                        key,
                        dispatch.id(),
                        dispatch.targets().size());
                return;
            }
            if (schedule.job().runRecording() == JobRunRecording.NONE) {
                if (trigger(executor, schedule, scheduledAt)) {
                    log.trace("Quartz fire 已执行无记录 Job | scheduleKey={} scheduledAt={}", key, scheduledAt);
                } else {
                    log.debug("Quartz fire 因同键工作仍在执行而跳过 | scheduleKey={}", key);
                }
                return;
            }
            // 使用 Quartz 原定触发时间去重；禁止重叠时空结果表示本次触发按计划跳过。
            Optional<Id> runId = submit(executor, schedule, scheduledAt);
            if (runId.isPresent()) {
                log.trace(
                        "Quartz fire 已提交 Job run | schedulerName={} scheduleKey={} definitionVersion={} scheduledAt={} runId={}",
                        context.getScheduler().getSchedulerName(),
                        key,
                        definitionVersion,
                        scheduledAt,
                        runId.get());
            } else {
                log.debug(
                        "Quartz fire 因前一次运行未结束而跳过 | schedulerName={} scheduleKey={} definitionVersion={} scheduledAt={}",
                        context.getScheduler().getSchedulerName(),
                        key,
                        definitionVersion,
                        scheduledAt);
            }
        } catch (SchedulerException | RuntimeException exception) {
            throw new JobExecutionException("Quartz fire 执行 Job 计划失败", exception, false);
        }
    }

    /** 保留计划泛型关系地调用 JobExecutor。 */
    private static <I, O> @NotNull Optional<Id> submit(
            @NotNull JobExecutor executor,
            @NotNull JobSchedule<I, O> schedule,
            @NotNull Instant scheduledAt) {
        return executor.submitScheduled(
                schedule.job(),
                schedule.input(),
                schedule.key(),
                scheduledAt,
                schedule.overlapPolicy());
    }

    /** 保留计划输入类型后执行无记录 Job。 */
    private static <I, O> boolean trigger(
            @NotNull JobExecutor executor,
            @NotNull JobSchedule<I, O> schedule,
            @NotNull Instant scheduledAt) {
        return executor.triggerScheduled(
                schedule.job(),
                schedule.input(),
                schedule.key(),
                scheduledAt,
                schedule.overlapPolicy());
    }

    /** 保留计划输入类型后提交同一原定 fire 的广播。 */
    private static <I, O> @NotNull JobDispatch broadcast(
            @NotNull JobExecutor executor,
            @NotNull JobSchedule<I, O> schedule,
            @NotNull Instant scheduledAt) {
        return executor.broadcastScheduled(
                schedule.job(),
                schedule.input(),
                schedule.key(),
                scheduledAt,
                schedule.overlapPolicy());
    }

    private static <I, O> boolean broadcastTarget(@NotNull JobSchedule<I, O> schedule) {
        return schedule.job().target(schedule.input()).kind() == JobTarget.Kind.ALL;
    }

    /**
     * 判断计划是否可以由任一实例直接执行：无记录且目标为任一实例时返回 true，不需要持久化 JobExecutor。
     */
    static <I, O> boolean quartzCanRunDirectly(@NotNull JobSchedule<I, O> schedule) {
        return schedule.job().runRecording() == JobRunRecording.NONE
                && schedule.job().target(schedule.input()).kind() == JobTarget.Kind.ANY;
    }

    /** 在只安装 Quartz、没有 Job 模块时执行无记录的任一实例计划。 */
    @SuppressWarnings("unchecked")
    private static <I, O> void directWithoutJobModule(
            @NotNull JobExecutionContext context,
            @NotNull JobSchedule<I, O> schedule) throws SchedulerException {
        if (!(schedule.job() instanceof DirectJob<?, ?> directJob)) {
            throw new IllegalStateException("无记录计划需要 DirectJob: " + schedule.key());
        }
        if (schedule.overlapPolicy() == OverlapPolicy.WAIT) {
            throw new IllegalArgumentException("无记录计划不能等待持久化队列: " + schedule.key());
        }
        var schedulerContext = context.getScheduler().getContext();
        InstanceCapabilities capabilities = (InstanceCapabilities) schedulerContext.get(CAPABILITIES_CONTEXT_KEY);
        InstanceProperties instanceProperties = (InstanceProperties) schedulerContext.get(INSTANCE_CONTEXT_KEY);
        DistributedLock locks = (DistributedLock) schedulerContext.get(LOCK_CONTEXT_KEY);
        Clock clock = (Clock) schedulerContext.get(CLOCK_CONTEXT_KEY);
        TreeCode capabilityCode = schedule.job().requiredCapability();
        var capability = capabilities.definitions().get(capabilityCode);
        if (capability == null || !capabilities.enabled(capabilityCode)) {
            throw new IllegalStateException("当前 Quartz 实例未启用 Job 能力: " + capabilityCode);
        }
        var principal = SystemPrincipals.require(capability.systemIdentity());
        var workKey = Objects.requireNonNull(schedule.job().workKey(schedule.input()), "Job 工作键不能为空");
        LockKey lockKey = new LockKey(
                TreeCode.ofColon("JOB:WORK"),
                principal.code().format(COLON),
                schedule.job().key().format(COLON),
                workKey.value());
        Runnable action = () -> {
            UUID executionId = UUID.randomUUID();
            Instant startedAt = clock.instant();
            JobExecution execution = new JobExecution(
                    executionId,
                    null,
                    1,
                    startedAt,
                    new InstanceId(instanceProperties.getId()),
                    capabilityCode,
                    principal);
            try {
                Object output = CallHolder.run(
                        Call.system(executionId.toString(), startedAt, principal),
                        () -> ((DirectJob<I, O>) directJob).run(execution, schedule.input()));
                if (output == null && !Void.class.equals(schedule.job().outputType())
                        || output != null && !schedule.job().outputType().isInstance(output)) {
                    throw new IllegalStateException("无记录 Job 输出类型不一致: " + schedule.job().key());
                }
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new SchedulingQuartzException("Quartz 无记录 Job 执行失败: " + schedule.key(), exception);
            }
        };
        if (schedule.overlapPolicy() == OverlapPolicy.ALLOW) {
            action.run();
            return;
        }
        AtomicBoolean entered = new AtomicBoolean();
        try {
            locks.execute(lockKey, Duration.ZERO, () -> {
                entered.set(true);
                action.run();
                return null;
            });
        } catch (BusinessException exception) {
            if (!entered.get() && exception.errorCode() == CoreErrorCode.CONFLICT) {
                log.debug("Quartz fire 因同键无记录工作仍在执行而跳过 | scheduleKey={}", schedule.key());
                return;
            }
            throw exception;
        }
    }
}
