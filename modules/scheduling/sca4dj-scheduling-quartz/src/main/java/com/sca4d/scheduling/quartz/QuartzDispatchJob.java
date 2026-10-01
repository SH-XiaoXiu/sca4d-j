package com.sca4d.scheduling.quartz;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.job.JobExecutor;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.NotNull;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/** 把一次 Quartz fire 转换成同一原定时间的持久化 Job run 提交。 */
public class QuartzDispatchJob implements Job {

    /** Quartz SchedulerContext 中保存 JobExecutor 的键。 */
    public static final String EXECUTOR_CONTEXT_KEY = "sca4djJobExecutor";

    /** Quartz SchedulerContext 中保存计划目录的键。 */
    public static final String REGISTRY_CONTEXT_KEY = "sca4djScheduleRegistry";

    /** JobDataMap 中保存计划定义版本的键。 */
    public static final String SCHEDULE_DEFINITION_VERSION = "scheduleDefinitionVersion";

    /** JobDataMap 中保存稳定计划 key 的键。 */
    public static final String SCHEDULE_KEY = "scheduleKey";

    private static final Logger log = LoggerFactory.getLogger(QuartzDispatchJob.class);

    public QuartzDispatchJob() {
    }

    @Override
    public void execute(@NotNull JobExecutionContext context) throws JobExecutionException {
        try {
            // JobDataMap 只保存稳定定位信息，实际计划从本实例完成对账后的目录恢复。
            JobDataMap data = context.getMergedJobDataMap();
            ScheduleKey key = new ScheduleKey(data.getString(SCHEDULE_KEY));
            int definitionVersion = Integer.parseInt(data.getString(SCHEDULE_DEFINITION_VERSION));
            QuartzScheduleRegistry registry = (QuartzScheduleRegistry) context.getScheduler()
                    .getContext()
                    .get(REGISTRY_CONTEXT_KEY);
            JobExecutor executor = (JobExecutor) context.getScheduler().getContext().get(EXECUTOR_CONTEXT_KEY);
            JobSchedule<?, ?> schedule = registry.loadByKeyAndVersion(key, definitionVersion);
            Instant scheduledAt = context.getScheduledFireTime().toInstant();
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
            throw new JobExecutionException("Quartz fire 无法提交持久化 Job run", exception, false);
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
}
