package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.TriggerSource;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** Schedule 单次执行持久化实体。 */
@Data
@Accessors(chain = true)
@Table("schedule_execution")
public class ScheduleExecutionEntity {
    @Id
    private Long executionId;
    private Long scheduleId;
    private String fireKey;
    private TriggerSource triggerSource;
    private OffsetDateTime scheduledFireTime;
    private OffsetDateTime actualFireTime;
    private OffsetDateTime completedAt;
    private ExecutionStatus status;
    private String taskType;
    private String resultSummary;
    private String safeErrorType;
    private String safeErrorMessage;
    private String executedInstanceId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
}
