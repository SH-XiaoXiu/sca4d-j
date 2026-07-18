package com.sca4d.schedule.quartz;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

import com.sca4d.common.exception.BizException;
import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.schedule.ScheduleErrorCode;
import com.sca4d.schedule.SchedulePlanSynchronizer;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.repository.ScheduleRepository;
import org.jetbrains.annotations.NotNull;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/** 以 schedule_definition 为唯一数据来源的业务 Quartz Job 和 Trigger 对账实现。 */
@Component
public class ScheduleQuartzSynchronizer implements SchedulePlanSynchronizer {

    /** 所有业务 Schedule 在 Quartz 中使用的 Job 和 Trigger group。 */
    public static final String GROUP = "business";

    private final Scheduler scheduler;
    private final ScheduleRepository repository;

    public ScheduleQuartzSynchronizer(@NotNull Scheduler scheduler, @NotNull ScheduleRepository repository) {
        this.scheduler = scheduler;
        this.repository = repository;
    }

    @Override
    public void synchronize(@NotNull Long scheduleId) {
        ScheduleDefinitionEntity schedule = repository.findIncludingCancelled(scheduleId)
                .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        try {
            synchronize(schedule);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Schedule Quartz 同步失败", exception);
        }
    }

    @Override
    public void remove(@NotNull Long scheduleId) {
        try {
            scheduler.deleteJob(jobKey(scheduleId));
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Schedule Quartz 删除失败", exception);
        }
    }

    @Override
    public void reconcileAll() {
        try {
            Set<JobKey> expectedJobs = new HashSet<>();
            for (ScheduleDefinitionEntity schedule : repository.findForReconciliation()) {
                if (!schedule.getStatus().terminal() && schedule.getDeleted() == 0) {
                    expectedJobs.add(jobKey(schedule.getId()));
                }
                synchronize(schedule);
            }
            for (ScheduleExecutionEntity execution : repository.findPendingManualExecutions()) {
                scheduleManualExecution(execution.getScheduleId(), execution.getExecutionId());
            }
            for (JobKey storedKey : scheduler.getJobKeys(jobGroupEquals(GROUP))) {
                JobDetail stored = scheduler.getJobDetail(storedKey);
                if (stored != null
                        && stored.getJobClass().equals(BusinessScheduleJob.class)
                        && storedKey.getName().startsWith("schedule-")
                        && !expectedJobs.contains(storedKey)) {
                    scheduler.deleteJob(storedKey);
                }
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Schedule Quartz 全量对账失败", exception);
        }
    }

    @Override
    public void startManualExecution(@NotNull Long scheduleId, @NotNull Long executionId) {
        try {
            scheduleManualExecution(scheduleId, executionId);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Schedule 人工执行触发失败", exception);
        }
    }

    /**
     * 使一个 Schedule 的 Job、Trigger 和暂停状态与当前数据库数据一致。
     *
     * @param schedule 包含内部终态的数据库定义
     * @throws SchedulerException Quartz JDBC JobStore 读取或原子写入失败时抛出
     */
    private void synchronize(@NotNull ScheduleDefinitionEntity schedule) throws SchedulerException {
        JobKey jobKey = jobKey(schedule.getId());
        TriggerKey triggerKey = triggerKey(schedule.getId());
        if (schedule.getDeleted() != 0 || schedule.getStatus().terminal()) {
            scheduler.deleteJob(jobKey);
            return;
        }

        JobDetail expectedJob = newJob(BusinessScheduleJob.class)
                .withIdentity(jobKey)
                .withDescription("Schedule " + schedule.getId())
                .storeDurably()
                .requestRecovery(true)
                .usingJobData("scheduleId", schedule.getId())
                .build();
        Trigger expectedTrigger;
        if (schedule.getScheduleType() == ScheduleType.ONCE) {
            expectedTrigger = newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withDescription(schedule.getDefinitionFingerprint())
                    .startAt(Date.from(schedule.getExecuteAt().toInstant()))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                    .build();
        } else {
            expectedTrigger = newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withDescription(schedule.getDefinitionFingerprint())
                    .withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression())
                            .inTimeZone(TimeZone.getTimeZone(ZoneId.of(schedule.getTimeZone())))
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
        }

        JobDetail storedJob = scheduler.getJobDetail(jobKey);
        if (storedJob == null) {
            try {
                scheduler.scheduleJob(expectedJob, expectedTrigger);
                storedJob = expectedJob;
            } catch (ObjectAlreadyExistsException exception) {
                storedJob = scheduler.getJobDetail(jobKey);
                if (storedJob == null) {
                    throw exception;
                }
            }
        }
        if (!storedJob.getJobClass().equals(BusinessScheduleJob.class)
                || !storedJob.isDurable()
                || !storedJob.requestsRecovery()
                || storedJob.getJobDataMap().size() != 1
                || storedJob.getJobDataMap().getLongValue("scheduleId") != schedule.getId()) {
            throw new SchedulerException("业务 Schedule Job 不可变定义冲突: " + jobKey);
        }
        Trigger storedTrigger = scheduler.getTrigger(triggerKey);
        if (storedTrigger == null) {
            try {
                scheduler.scheduleJob(expectedTrigger);
                storedTrigger = expectedTrigger;
            } catch (ObjectAlreadyExistsException exception) {
                storedTrigger = scheduler.getTrigger(triggerKey);
                if (storedTrigger == null) {
                    throw exception;
                }
            }
        }
        if (!triggerMatches(schedule, storedTrigger, jobKey)) {
            scheduler.rescheduleJob(triggerKey, expectedTrigger);
        }

        if (schedule.getStatus() == ScheduleStatus.PAUSED) {
            scheduler.pauseTrigger(triggerKey);
        } else if (scheduler.getTriggerState(triggerKey) == Trigger.TriggerState.PAUSED) {
            scheduler.resumeTrigger(triggerKey);
        }
    }

    /**
     * 比较 Quartz Trigger 是否仍准确投影当前 Schedule 规则。
     *
     * @param schedule 当前数据库业务数据
     * @param trigger Quartz JDBC JobStore 中的现有 Trigger
     * @param jobKey 当前 Schedule 的固定 JobKey
     * @return 类型、JobKey、misfire、时间或 Cron 规则全部一致时返回 true
     */
    private static boolean triggerMatches(
            @NotNull ScheduleDefinitionEntity schedule, @NotNull Trigger trigger, @NotNull JobKey jobKey) {
        if (!trigger.getJobKey().equals(jobKey)
                || !schedule.getDefinitionFingerprint().equals(trigger.getDescription())) {
            return false;
        }
        if (schedule.getScheduleType() == ScheduleType.ONCE) {
            return trigger instanceof SimpleTrigger simple
                    && simple.getStartTime().toInstant().equals(schedule.getExecuteAt().toInstant())
                    && simple.getMisfireInstruction() == SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW;
        }
        return trigger instanceof CronTrigger cron
                && cron.getCronExpression().equals(schedule.getCronExpression())
                && cron.getTimeZone().toZoneId().equals(ZoneId.of(schedule.getTimeZone()))
                && cron.getMisfireInstruction() == CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING;
    }

    /**
     * 为一条 PENDING 人工执行数据创建可恢复的一次性 Trigger。
     *
     * <p>TriggerKey 由 executionId 确定。启动对账和事务提交回调并发时，重复创建只接受完全相同的
     * JobKey 与 executionId，不会形成第二种人工执行含义。
     *
     * @param scheduleId 当前 ACTIVE 或 PAUSED Schedule 主键
     * @param executionId 已持久化的 PENDING 人工执行主键
     * @throws SchedulerException Quartz JDBC JobStore 写入或冲突定义读取失败时抛出
     */
    private void scheduleManualExecution(@NotNull Long scheduleId, @NotNull Long executionId)
            throws SchedulerException {
        JobDataMap data = new JobDataMap();
        data.put("executionId", executionId);
        TriggerKey manualTriggerKey = new TriggerKey("schedule-" + scheduleId + "-manual-" + executionId, GROUP);
        Trigger trigger = newTrigger()
                .withIdentity(manualTriggerKey)
                .forJob(jobKey(scheduleId))
                .usingJobData(data)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
        try {
            scheduler.scheduleJob(trigger);
        } catch (ObjectAlreadyExistsException exception) {
            Trigger stored = scheduler.getTrigger(manualTriggerKey);
            if (stored == null
                    || !stored.getJobKey().equals(jobKey(scheduleId))
                    || stored.getJobDataMap().getLongValue("executionId") != executionId) {
                throw exception;
            }
        }
    }

    /** @param scheduleId Schedule 主键 @return business 分组的固定 JobKey */
    private static @NotNull JobKey jobKey(@NotNull Long scheduleId) {
        return new JobKey("schedule-" + scheduleId, GROUP);
    }

    /** @param scheduleId Schedule 主键 @return business 分组的固定 TriggerKey */
    private static @NotNull TriggerKey triggerKey(@NotNull Long scheduleId) {
        return new TriggerKey("schedule-" + scheduleId + "-trigger", GROUP);
    }
}
