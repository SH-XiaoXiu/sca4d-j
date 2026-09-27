package com.sca4d.iot.schedule;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 标识一个到期规则生成 command 时发生的业务失败，供新事务保存 FAILED 结果。 */
public class DeviceScheduleTriggerFailure extends RuntimeException {

    private final long scheduleId;

    private final long scheduleVersion;

    private final @NotNull Instant plannedAt;

    private final @NotNull String failureCode;

    /** 创建能够重新定位同版本到期规则的业务失败。 */
    public DeviceScheduleTriggerFailure(
            long scheduleId,
            long scheduleVersion,
            @NotNull Instant plannedAt,
            @NotNull String failureCode,
            @NotNull Throwable cause) {
        super("设备定时生成 command 失败", Objects.requireNonNull(cause, "设备定时失败原因不能为空"));
        if (scheduleId <= 0 || scheduleVersion < 0) {
            throw new IllegalArgumentException("设备定时标识或版本不合法");
        }
        this.scheduleId = scheduleId;
        this.scheduleVersion = scheduleVersion;
        this.plannedAt = Objects.requireNonNull(plannedAt, "设备定时原计划时间不能为空");
        this.failureCode = TextUtils.requireText(failureCode, "设备定时失败 code");
    }

    /** @return 失败规则标识 */
    public long scheduleId() {
        return scheduleId;
    }

    /** @return 失败规则版本 */
    public long scheduleVersion() {
        return scheduleVersion;
    }

    /** @return 失败触发的原计划时间 */
    public @NotNull Instant plannedAt() {
        return plannedAt;
    }

    /** @return 可以保存和公开的业务失败编码 */
    public @NotNull String failureCode() {
        return failureCode;
    }
}
