package com.sca4d.system;

import com.sca4d.schedule.SystemJobResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;

/** 系统 Job 代码定义、Quartz 状态和最近运行快照的只读聚合。 */
public class SystemScheduledJob {
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

    public @NotNull String getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(@NotNull String jobGroup) {
        this.jobGroup = jobGroup;
    }

    public @NotNull String getJobName() {
        return jobName;
    }

    public void setJobName(@NotNull String jobName) {
        this.jobName = jobName;
    }

    public @NotNull String getDescription() {
        return description;
    }

    public void setDescription(@NotNull String description) {
        this.description = description;
    }

    public @NotNull SystemJobTriggerState getTriggerState() {
        return triggerState;
    }

    public void setTriggerState(@NotNull SystemJobTriggerState triggerState) {
        this.triggerState = triggerState;
    }

    public @NotNull SystemJobScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(@NotNull SystemJobScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public @Nullable Duration getInterval() {
        return interval;
    }

    public void setInterval(@Nullable Duration interval) {
        this.interval = interval;
    }

    public @Nullable String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(@Nullable String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public @Nullable OffsetDateTime getPreviousFireTime() {
        return previousFireTime;
    }

    public void setPreviousFireTime(@Nullable OffsetDateTime previousFireTime) {
        this.previousFireTime = previousFireTime;
    }

    public @Nullable OffsetDateTime getNextFireTime() {
        return nextFireTime;
    }

    public void setNextFireTime(@Nullable OffsetDateTime nextFireTime) {
        this.nextFireTime = nextFireTime;
    }

    public @Nullable OffsetDateTime getLastActualFireTime() {
        return lastActualFireTime;
    }

    public void setLastActualFireTime(@Nullable OffsetDateTime lastActualFireTime) {
        this.lastActualFireTime = lastActualFireTime;
    }

    public @Nullable SystemJobResult getLastResult() {
        return lastResult;
    }

    public void setLastResult(@Nullable SystemJobResult lastResult) {
        this.lastResult = lastResult;
    }

    public @Nullable Duration getLastDuration() {
        return lastDuration;
    }

    public void setLastDuration(@Nullable Duration lastDuration) {
        this.lastDuration = lastDuration;
    }

    public @Nullable String getSafeErrorSummary() {
        return safeErrorSummary;
    }

    public void setSafeErrorSummary(@Nullable String safeErrorSummary) {
        this.safeErrorSummary = safeErrorSummary;
    }

    public @Nullable String getExecutedInstanceId() {
        return executedInstanceId;
    }

    public void setExecutedInstanceId(@Nullable String executedInstanceId) {
        this.executedInstanceId = executedInstanceId;
    }

    public boolean isManageable() {
        return manageable;
    }

    public void setManageable(boolean manageable) {
        this.manageable = manageable;
    }
}
