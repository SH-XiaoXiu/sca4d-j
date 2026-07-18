package com.sca4d.schedule.quartz;

import com.sca4d.common.core.TimeSupport;
import com.sca4d.schedule.ScheduleExecutionService;
import com.sca4d.schedule.TriggerSource;
import org.jetbrains.annotations.NotNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 只携带 Schedule 标识并在执行前重新读取数据库数据的通用业务 Quartz Job。 */
@DisallowConcurrentExecution
public class BusinessScheduleJob implements Job {

    private ScheduleExecutionService executionService;

    /** 供 Quartz JobFactory 创建实例；依赖由 Spring AutowireCapableBeanFactory 注入。 */
    public BusinessScheduleJob() {
    }

    /** @param executionService 领取执行记录并运行 ScheduleTask 的服务 */
    @Autowired
    public void setExecutionService(@NotNull ScheduleExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * 从 JobDataMap 读取最小标识，并把真实校验、事务和任务分派交给 ScheduleExecutionService。
     *
     * @param context Quartz 当前 fire 的框架上下文
     * @throws JobExecutionException 执行失败时交由 Quartz 记录，但不立即重复 refire
     */
    @Override
    public void execute(@NotNull JobExecutionContext context) throws JobExecutionException {
        try {
            Long scheduleId = context.getJobDetail().getJobDataMap().getLongValue("scheduleId");
            Long executionId = context.getMergedJobDataMap().containsKey("executionId")
                    ? context.getMergedJobDataMap().getLongValue("executionId")
                    : null;
            TriggerSource source = context.isRecovering()
                    ? TriggerSource.RECOVERY
                    : executionId != null ? TriggerSource.MANUAL : TriggerSource.SCHEDULED;
            OffsetDateTime scheduledFireTime;
            if (context.isRecovering()) {
                String originalScheduledTime = context.getMergedJobDataMap()
                        .getString(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS);
                scheduledFireTime = Instant.ofEpochMilli(Long.parseLong(originalScheduledTime))
                        .atOffset(ZoneOffset.UTC);
            } else {
                scheduledFireTime = context.getScheduledFireTime() == null
                        ? null
                        : context.getScheduledFireTime().toInstant().atOffset(ZoneOffset.UTC);
            }
            executionService.run(
                    scheduleId,
                    executionId,
                    source == TriggerSource.SCHEDULED ? context.getTrigger().getDescription() : null,
                    source,
                    scheduledFireTime,
                    TimeSupport.now(),
                    context.getScheduler().getSchedulerInstanceId());
        } catch (Exception exception) {
            throw new JobExecutionException(exception, false);
        }
    }
}
