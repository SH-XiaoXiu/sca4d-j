package com.sca4d.iot.persistence;

import com.sca4d.iot.api.schedule.DeviceScheduleStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 映射一条设备定时规则的持久化状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EncodedDeviceSchedule {

    private final long scheduleId;

    private final long deviceId;

    private final @NotNull String commandCode;

    private final @NotNull String commandInputsJson;

    private final @NotNull String triggerType;

    private final @Nullable Instant executeAt;

    private final @Nullable String cronExpression;

    private final @Nullable String zoneId;

    private final long validForMillis;

    private final @NotNull DeviceScheduleStatus status;

    private final @Nullable Instant nextFireAt;

    private final long version;

    /** 创建完整设备定时规则查询结果。 */
    @SuppressWarnings("ParameterNumber")
    public EncodedDeviceSchedule(
            long scheduleId,
            long deviceId,
            @NotNull String commandCode,
            @NotNull String commandInputsJson,
            @NotNull String triggerType,
            @Nullable Instant executeAt,
            @Nullable String cronExpression,
            @Nullable String zoneId,
            long validForMillis,
            @NotNull DeviceScheduleStatus status,
            @Nullable Instant nextFireAt,
            long version) {
        this.scheduleId = scheduleId;
        this.deviceId = deviceId;
        this.commandCode = commandCode;
        this.commandInputsJson = commandInputsJson;
        this.triggerType = triggerType;
        this.executeAt = executeAt;
        this.cronExpression = cronExpression;
        this.zoneId = zoneId;
        this.validForMillis = validForMillis;
        this.status = status;
        this.nextFireAt = nextFireAt;
        this.version = version;
    }

    /** @return 定时规则标识 */
    public long scheduleId() {
        return scheduleId;
    }

    /** @return Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return command code */
    public @NotNull String commandCode() {
        return commandCode;
    }

    /** @return command 输入 JSON */
    public @NotNull String commandInputsJson() {
        return commandInputsJson;
    }

    /** @return ONCE 或 CRON */
    public @NotNull String triggerType() {
        return triggerType;
    }

    /** @return 单次执行时间 */
    public @Nullable Instant executeAt() {
        return executeAt;
    }

    /** @return Cron 表达式 */
    public @Nullable String cronExpression() {
        return cronExpression;
    }

    /** @return Cron 时区 */
    public @Nullable String zoneId() {
        return zoneId;
    }

    /** @return 下行有效毫秒数 */
    public long validForMillis() {
        return validForMillis;
    }

    /** @return 规则状态 */
    public @NotNull DeviceScheduleStatus status() {
        return status;
    }

    /** @return 下一次触发时间 */
    public @Nullable Instant nextFireAt() {
        return nextFireAt;
    }

    /** @return 乐观锁版本 */
    public long version() {
        return version;
    }
}
