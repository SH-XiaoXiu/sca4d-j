package com.sca4d.system.service;

import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.entity.ScheduleJobRuntimeEntity;
import com.sca4d.schedule.quartz.SystemJobDefinition;
import com.sca4d.schedule.quartz.SystemJobQuartzSynchronizer;
import com.sca4d.schedule.repository.ScheduleRepository;
import com.sca4d.system.SystemJobScheduleType;
import com.sca4d.system.SystemJobTriggerState;
import com.sca4d.system.SystemScheduledJob;
import com.sca4d.system.SystemScheduledJobService;
import org.jetbrains.annotations.NotNull;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 系统 Job 只读查询实现。 */
@Service
public class DefaultSystemScheduledJobService implements SystemScheduledJobService {

    private final Scheduler scheduler;
    private final SystemJobQuartzSynchronizer systemJobs;
    private final ScheduleRepository repository;

    public DefaultSystemScheduledJobService(
            @NotNull Scheduler scheduler,
            @NotNull SystemJobQuartzSynchronizer systemJobs,
            @NotNull ScheduleRepository repository) {
        this.scheduler = scheduler;
        this.systemJobs = systemJobs;
        this.repository = repository;
    }

    @Override
    public @NotNull List<SystemScheduledJob> jobs() {
        List<SystemJobDefinition> definitions = systemJobs.definitions();
        // 运行快照固定执行一条 system 分组批量查询；Quartz 每个固定 JobKey 各读取一次 Trigger 和状态。
        Map<String, ScheduleJobRuntimeEntity> runtimes = repository.findSystemJobRuntimes(
                definitions.stream().map(SystemJobDefinition::getJobName).toList());
        return definitions.stream().map(definition -> job(definition, runtimes.get(definition.getJobName()))).toList();
    }

    @Override
    public @NotNull SystemScheduledJob require(@NotNull String jobGroup, @NotNull String jobName) {
        if (!SystemJobQuartzSynchronizer.GROUP.equals(jobGroup)) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        return jobs().stream()
                .filter(job -> job.getJobName().equals(jobName))
                .findFirst()
                .orElseThrow(() -> new BizException(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 合并代码定义、Quartz Trigger 和最近运行快照。
     *
     * @param definition 固定系统 Job 定义
     * @param runtime 尚未执行时为空的最近运行快照
     * @return 只包含定义和运行状态、不能用于修改 Job 的业务对象
     */
    private @NotNull SystemScheduledJob job(
            @NotNull SystemJobDefinition definition, ScheduleJobRuntimeEntity runtime) {
        try {
            TriggerKey triggerKey = new TriggerKey(definition.getTriggerName(), SystemJobQuartzSynchronizer.GROUP);
            Trigger trigger = scheduler.getTrigger(triggerKey);
            Trigger.TriggerState quartzState = scheduler.getTriggerState(triggerKey);
            SystemScheduledJob job = new SystemScheduledJob();
            job.setJobGroup(SystemJobQuartzSynchronizer.GROUP);
            job.setJobName(definition.getJobName());
            job.setDescription(definition.getDescription());
            job.setTriggerState(SystemJobTriggerState.valueOf(quartzState.name()));
            job.setScheduleType(SystemJobScheduleType.INTERVAL);
            job.setInterval(definition.getInterval());
            job.setPreviousFireTime(trigger == null ? null : TimeSupport.from(trigger.getPreviousFireTime()));
            job.setNextFireTime(trigger == null ? null : TimeSupport.from(trigger.getNextFireTime()));
            job.setManageable(false);
            if (runtime != null) {
                job.setLastActualFireTime(runtime.getLastActualFireTime());
                job.setLastResult(runtime.getLastResult());
                job.setLastDuration(runtime.getLastDurationMillis() == null
                        ? null
                        : Duration.ofMillis(runtime.getLastDurationMillis()));
                job.setSafeErrorSummary(runtime.getSafeErrorSummary());
                job.setExecutedInstanceId(runtime.getExecutedInstanceId());
            }
            return job;
        } catch (SchedulerException exception) {
            throw new IllegalStateException("系统 Job Quartz 状态读取失败", exception);
        }
    }
}
