package com.sca4d.schedule;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Schedule 的一次计划、人工或恢复执行。 */
@Getter
@AllArgsConstructor
public class ScheduleExecution {

    private final Long executionId;
    private final Long scheduleId;
    private final TriggerSource triggerSource;
    private final OffsetDateTime scheduledFireTime;
    private final OffsetDateTime actualFireTime;
    private final OffsetDateTime completedAt;
    private final ExecutionStatus status;
    private final String taskType;
    private final String resultSummary;
    private final String safeErrorType;
    private final String safeErrorMessageKey;
    private final String executedInstanceId;
    private final OffsetDateTime createdAt;
}
