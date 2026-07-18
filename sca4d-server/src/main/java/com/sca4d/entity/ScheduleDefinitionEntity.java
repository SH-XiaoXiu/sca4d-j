package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** Schedule 业务定义持久化实体。 */
@Data
@Accessors(chain = true)
@Table("schedule_definition")
public class ScheduleDefinitionEntity {
    private Long id;
    private String name;
    private ScheduleType scheduleType;
    private OffsetDateTime executeAt;
    private String cronExpression;
    private String timeZone;
    private ScheduleStatus status;
    private String taskType;
    private String taskPayload;
    private Integer taskPayloadVersion;
    private String definitionFingerprint;
    private String synchronizationError;
    private Long createdByPrincipalId;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer deleted;
}
