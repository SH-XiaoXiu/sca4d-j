package com.sca4d.schedule.view;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.schedule.Schedule;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/** Schedule 主资源的完整管理端表示。 */
@Data
@Accessors(chain = true)
public class ScheduleView {
    private Long scheduleId;
    private String name;
    private ScheduleType scheduleType;
    private OffsetDateTime executeAt;
    private String cronExpression;
    private String timeZone;
    private ScheduleStatus status;
    private String taskType;
    private JsonNode taskPayload;
    private Integer taskPayloadVersion;
    private String synchronizationError;
    private Long createdByPrincipalId;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * 将 Schedule 业务定义转换为包含类型化 JSON 节点的 HTTP 资源表示。
     *
     * @param schedule 当前未删除 Schedule 定义
     * @param objectMapper 项目共用 JSON 契约
     * @return 完整管理端表示
     */
    public static @NotNull ScheduleView from(
            @NotNull Schedule schedule, @NotNull ObjectMapper objectMapper) {
        try {
            return new ScheduleView()
                    .setScheduleId(schedule.getScheduleId())
                    .setName(schedule.getName())
                    .setScheduleType(schedule.getScheduleType())
                    .setExecuteAt(schedule.getExecuteAt())
                    .setCronExpression(schedule.getCronExpression())
                    .setTimeZone(schedule.getTimeZone())
                    .setStatus(schedule.getStatus())
                    .setTaskType(schedule.getTaskType())
                    .setTaskPayload(objectMapper.readTree(schedule.getTaskPayload()))
                    .setTaskPayloadVersion(schedule.getTaskPayloadVersion())
                    .setSynchronizationError(schedule.getSynchronizationError())
                    .setCreatedByPrincipalId(schedule.getCreatedByPrincipalId())
                    .setVersion(schedule.getVersion())
                    .setCreatedAt(schedule.getCreatedAt())
                    .setUpdatedAt(schedule.getUpdatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Schedule Payload 读取失败", exception);
        }
    }
}
