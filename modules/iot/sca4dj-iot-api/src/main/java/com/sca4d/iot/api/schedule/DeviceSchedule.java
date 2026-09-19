package com.sca4d.iot.api.schedule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 保存一个持续负责生成设备 command 下行的定时规则。 */
public final class DeviceSchedule {

    private final long scheduleId;

    private final long deviceId;

    private final @NotNull DeviceScheduledCommand command;

    private final @NotNull DeviceScheduleTrigger trigger;

    private final @NotNull Duration validFor;

    private final @NotNull DeviceScheduleStatus status;

    private final @Nullable Instant nextFireAt;

    private final long version;

    /** 创建已经持久化并计算下一次触发时间的设备定时规则。 */
    @SuppressWarnings("ParameterNumber")
    public DeviceSchedule(
            long scheduleId,
            long deviceId,
            @NotNull DeviceScheduledCommand command,
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Duration validFor,
            @NotNull DeviceScheduleStatus status,
            @Nullable Instant nextFireAt,
            long version) {
        if (scheduleId <= 0 || deviceId <= 0 || version < 0) {
            throw new IllegalArgumentException("设备定时标识或版本不合法");
        }
        this.scheduleId = scheduleId;
        this.deviceId = deviceId;
        this.command = Objects.requireNonNull(command, "设备定时 command 不能为空");
        this.trigger = Objects.requireNonNull(trigger, "设备定时触发规则不能为空");
        this.validFor = Objects.requireNonNull(validFor, "设备定时有效期不能为空");
        this.status = Objects.requireNonNull(status, "设备定时状态不能为空");
        this.nextFireAt = nextFireAt;
        this.version = version;
        if (validFor.isZero() || validFor.isNegative()) {
            throw new IllegalArgumentException("设备定时有效期必须为正数");
        }
    }

    /** @return 定时规则标识 */
    public long scheduleId() {
        return scheduleId;
    }

    /** @return 目标 Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return 每次触发时重新校验的 command */
    public @NotNull DeviceScheduledCommand command() {
        return command;
    }

    /** @return 单次或 Cron 触发规则 */
    public @NotNull DeviceScheduleTrigger trigger() {
        return trigger;
    }

    /** @return 从计划时间开始计算的下行有效期 */
    public @NotNull Duration validFor() {
        return validFor;
    }

    /** @return 当前规则状态 */
    public @NotNull DeviceScheduleStatus status() {
        return status;
    }

    /** @return 下一次绝对触发时间；暂停、取消或单次完成后为空 */
    public @NotNull Optional<Instant> nextFireAt() {
        return Optional.ofNullable(nextFireAt);
    }

    /** @return 乐观锁版本 */
    public long version() {
        return version;
    }
}
