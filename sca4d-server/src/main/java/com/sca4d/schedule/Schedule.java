package com.sca4d.schedule;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 可由管理端读取和修改的 Schedule 业务定义。 */
@Getter
@AllArgsConstructor
public class Schedule {

    private final Long scheduleId;
    private final String name;
    private final ScheduleType scheduleType;
    private final OffsetDateTime executeAt;
    private final String cronExpression;
    private final String timeZone;
    private final ScheduleStatus status;
    private final String taskType;
    private final String taskPayload;
    private final Integer taskPayloadVersion;
    private final String synchronizationError;
    private final Long createdByPrincipalId;
    private final Long version;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
