package com.sca4d.iot.api.schedule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** 保存一次设备定时计划时间对应的下行生成结果。 */
public class DeviceScheduleExecution {

    private final long executionId;

    private final long scheduleId;

    private final @NotNull Instant plannedAt;

    private final @NotNull Instant triggeredAt;

    private final @NotNull DeviceScheduleExecutionStatus status;

    private final @Nullable Long messageId;

    private final @Nullable String failureCode;

    /** 创建一条幂等设备定时执行记录。 */
    @SuppressWarnings("ParameterNumber")
    public DeviceScheduleExecution(
            long executionId,
            long scheduleId,
            @NotNull Instant plannedAt,
            @NotNull Instant triggeredAt,
            @NotNull DeviceScheduleExecutionStatus status,
            @Nullable Long messageId,
            @Nullable String failureCode) {
        if (executionId <= 0 || scheduleId <= 0 || messageId != null && messageId <= 0) {
            throw new IllegalArgumentException("设备定时执行标识不合法");
        }
        this.executionId = executionId;
        this.scheduleId = scheduleId;
        this.plannedAt = Objects.requireNonNull(plannedAt, "设备定时计划时间不能为空");
        this.triggeredAt = Objects.requireNonNull(triggeredAt, "设备定时触发时间不能为空");
        this.status = Objects.requireNonNull(status, "设备定时执行状态不能为空");
        this.messageId = messageId;
        this.failureCode = failureCode;
    }

    /** @return 执行记录标识 */
    public long executionId() {
        return executionId;
    }

    /** @return 定时规则标识 */
    public long scheduleId() {
        return scheduleId;
    }

    /** @return 规则计算出的计划时间 */
    public @NotNull Instant plannedAt() {
        return plannedAt;
    }

    /** @return Timer 实际开始处理时间 */
    public @NotNull Instant triggeredAt() {
        return triggeredAt;
    }

    /** @return 下行生成结果 */
    public @NotNull DeviceScheduleExecutionStatus status() {
        return status;
    }

    /** @return 已生成的下行 messageId */
    public @NotNull OptionalLong messageId() {
        return messageId == null ? OptionalLong.empty() : OptionalLong.of(messageId);
    }

    /** @return 可由消息目录翻译的稳定失败 code；成功或过期时为空 */
    public @NotNull Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }
}
