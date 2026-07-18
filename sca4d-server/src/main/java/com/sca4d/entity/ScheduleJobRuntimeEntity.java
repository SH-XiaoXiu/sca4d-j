package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.schedule.SystemJobResult;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** 系统 Quartz Job 最近一次运行快照实体。 */
@Data
@Accessors(chain = true)
@Table("schedule_job_runtime")
public class ScheduleJobRuntimeEntity {
    private String jobGroup;
    private String jobName;
    private OffsetDateTime lastActualFireTime;
    private SystemJobResult lastResult;
    private Long lastDurationMillis;
    private String safeErrorSummary;
    private String executedInstanceId;
    private OffsetDateTime updatedAt;
}
