package com.sca4d.iot.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 表示 Device 当前保存的一个 desired property 业务值。 */
public final class DesiredPropertyState {

    private final @NotNull String propertyCode;

    private final long desiredSequence;

    private final @NotNull Object value;

    private final @Nullable Instant convergedAt;

    /** 创建已经从数据库 JSON 解码的 desired property 状态。 */
    public DesiredPropertyState(
            @NotNull String propertyCode,
            long desiredSequence,
            @NotNull Object value,
            @Nullable Instant convergedAt) {
        this.propertyCode = Objects.requireNonNull(propertyCode, "desired property code 不能为空");
        this.desiredSequence = desiredSequence;
        this.value = Objects.requireNonNull(value, "desired property 值不能为空");
        this.convergedAt = convergedAt;
    }

    /** @return desired property code */
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return 写入该值时使用的 desired sequence */
    public long desiredSequence() {
        return desiredSequence;
    }

    /** @return 已解码的 desired property 值 */
    public @NotNull Object value() {
        return value;
    }

    /** @return 最近收敛时间，尚未收敛时为空 */
    public @Nullable Instant convergedAt() {
        return convergedAt;
    }
}
