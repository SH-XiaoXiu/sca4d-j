package com.sca4d.system.view;

import com.sca4d.schedule.SystemJobResult;
import com.sca4d.system.SystemJobScheduleType;
import com.sca4d.system.SystemJobTriggerState;
import com.sca4d.system.SystemScheduledJob;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.OffsetDateTime;

/** 系统 Quartz Job 定义、状态和最近结果的只读 HTTP 表示。 */
@Data
@Accessors(chain = true)
public class SystemScheduledJobView {
    private String jobGroup;
    private String jobName;
    private String description;
    private SystemJobTriggerState triggerState;
    private SystemJobScheduleType scheduleType;
    private Duration interval;
    private String cronExpression;
    private OffsetDateTime previousFireTime;
    private OffsetDateTime nextFireTime;
    private OffsetDateTime lastActualFireTime;
    private SystemJobResult lastResult;
    private Duration lastDuration;
    private String safeErrorSummary;
    private String executedInstanceId;
    private boolean manageable;

    /** @param job 系统 Job 只读业务对象 @return 管理端完整表示 */
    public static @NotNull SystemScheduledJobView from(@NotNull SystemScheduledJob job) {
        return new SystemScheduledJobView()
                .setJobGroup(job.getJobGroup())
                .setJobName(job.getJobName())
                .setDescription(job.getDescription())
                .setTriggerState(job.getTriggerState())
                .setScheduleType(job.getScheduleType())
                .setInterval(job.getInterval())
                .setCronExpression(job.getCronExpression())
                .setPreviousFireTime(job.getPreviousFireTime())
                .setNextFireTime(job.getNextFireTime())
                .setLastActualFireTime(job.getLastActualFireTime())
                .setLastResult(job.getLastResult())
                .setLastDuration(job.getLastDuration())
                .setSafeErrorSummary(job.getSafeErrorSummary())
                .setExecutedInstanceId(job.getExecutedInstanceId())
                .setManageable(job.isManageable());
    }
}
