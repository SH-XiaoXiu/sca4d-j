package com.sca4d.app.server.iot.view;

import com.sca4d.iot.api.schedule.DeviceSchedule;
import com.sca4d.iot.api.schedule.DeviceScheduleStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/** 返回 Device 定时规则、下一次触发时间和乐观版本。 */
@SuppressWarnings("unused")
public class IotDeviceScheduleView {

    private final @NotNull DeviceScheduleStatus status;

    private final @Nullable Instant executeAt;

    private final @Nullable Instant nextFireAt;

    private final @NotNull @Unmodifiable Map<String, Object> inputs;

    private final @NotNull String commandCode;

    private final @Nullable String cronExpression;

    private final @Nullable String zoneId;

    private final long deviceId;

    private final long scheduleId;

    private final long validForMillis;

    private final long version;

    /** 从模块 Device 定时规则创建 HTTP 视图。 */
    private IotDeviceScheduleView(@NotNull DeviceSchedule schedule) {
        this.scheduleId = schedule.scheduleId();
        this.deviceId = schedule.deviceId();
        this.commandCode = schedule.command().commandCode();
        this.inputs = schedule.command().inputs();
        this.executeAt = schedule.trigger().executeAt().orElse(null);
        this.cronExpression = schedule.trigger().cronExpression().orElse(null);
        this.zoneId = schedule.trigger().zoneId().map(ZoneId::getId).orElse(null);
        this.validForMillis = schedule.validFor().toMillis();
        this.status = schedule.status();
        this.nextFireAt = schedule.nextFireAt().orElse(null);
        this.version = schedule.version();
    }

    /** @return Device 定时规则 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotDeviceScheduleView from(@NotNull DeviceSchedule schedule) {
        return new IotDeviceScheduleView(Objects.requireNonNull(schedule, "Device 定时规则不能为空"));
    }

    /** @return 定时规则标识 */
    public long getScheduleId() {
        return scheduleId;
    }

    /** @return 目标 Device 标识 */
    public long getDeviceId() {
        return deviceId;
    }

    /** @return 物模型 command code */
    public @NotNull String getCommandCode() {
        return commandCode;
    }

    /** @return 完整 command 输入 */
    public @NotNull @Unmodifiable Map<String, Object> getInputs() {
        return Map.copyOf(inputs);
    }

    /** @return 单次执行时间；Cron 规则为空 */
    public @Nullable Instant getExecuteAt() {
        return executeAt;
    }

    /** @return 六字段 Cron；单次规则为空 */
    public @Nullable String getCronExpression() {
        return cronExpression;
    }

    /** @return Cron IANA 时区；单次规则为空 */
    public @Nullable String getZoneId() {
        return zoneId;
    }

    /** @return 从计划时间开始计算的下行有效毫秒数 */
    public long getValidForMillis() {
        return validForMillis;
    }

    /** @return 当前规则状态 */
    public @NotNull DeviceScheduleStatus getStatus() {
        return status;
    }

    /** @return 下一次触发时间；非 ACTIVE 规则为空 */
    public @Nullable Instant getNextFireAt() {
        return nextFireAt;
    }

    /** @return 乐观锁版本 */
    public long getVersion() {
        return version;
    }
}
