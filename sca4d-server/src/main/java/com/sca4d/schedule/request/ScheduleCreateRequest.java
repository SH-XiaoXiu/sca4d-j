package com.sca4d.schedule.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.common.validation.Required;
import com.sca4d.schedule.ScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/** 创建 Schedule 的完整计划和任务参数请求。 */
@Data
public class ScheduleCreateRequest {
    @NotBlank
    @Size(max = 200)
    private String name;

    @Required
    private ScheduleType scheduleType;

    private OffsetDateTime executeAt;
    private String cronExpression;
    private String timeZone;

    @NotBlank
    @Size(max = 128)
    private String taskType;

    @Required
    private JsonNode taskPayload;
}
