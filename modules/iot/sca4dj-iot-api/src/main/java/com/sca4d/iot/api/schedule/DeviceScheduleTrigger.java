package com.sca4d.iot.api.schedule;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/** 使用单次绝对时间或带时区的六字段 Cron 表达设备下行触发时间。 */
public final class DeviceScheduleTrigger {

    private final @Nullable Instant executeAt;

    private final @Nullable String cronExpression;

    private final @Nullable ZoneId zoneId;

    private DeviceScheduleTrigger(
            @Nullable Instant executeAt,
            @Nullable String cronExpression,
            @Nullable ZoneId zoneId) {
        this.executeAt = executeAt;
        this.cronExpression = cronExpression;
        this.zoneId = zoneId;
    }

    /** @return 只触发一次的绝对时间规则 */
    @CheckReturnValue
    public static @NotNull DeviceScheduleTrigger once(@NotNull Instant executeAt) {
        return new DeviceScheduleTrigger(
                Objects.requireNonNull(executeAt, "设备定时执行时间不能为空"),
                null,
                null);
    }

    /** @return 由 IoT 模块计算下一次触发时间的六字段 Cron 规则 */
    @CheckReturnValue
    public static @NotNull DeviceScheduleTrigger cron(
            @NotNull String cronExpression,
            @NotNull ZoneId zoneId) {
        return new DeviceScheduleTrigger(
                null,
                TextUtils.requireText(cronExpression, "设备定时 Cron"),
                Objects.requireNonNull(zoneId, "设备定时时区不能为空"));
    }

    /** @return 单次执行时间；Cron 规则为空 */
    public @NotNull Optional<Instant> executeAt() {
        return Optional.ofNullable(executeAt);
    }

    /** @return 六字段 Cron；单次规则为空 */
    public @NotNull Optional<String> cronExpression() {
        return Optional.ofNullable(cronExpression);
    }

    /** @return Cron 时区；单次规则为空 */
    public @NotNull Optional<ZoneId> zoneId() {
        return Optional.ofNullable(zoneId);
    }

    /** @return 当前规则是否只触发一次 */
    public boolean once() {
        return executeAt != null;
    }
}
