package com.sca4d.iot.api.query;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 保存一个 desired 属性当前持续期望的值和收敛状态。 */
public class DesiredStateSnapshot {

    private final @NotNull String propertyCode;

    private final long desiredSequence;

    private final @NotNull Object value;

    private final @Nullable Instant convergedAt;

    /** 创建 desired 属性查询快照。 */
    public DesiredStateSnapshot(
            @NotNull String propertyCode,
            long desiredSequence,
            @NotNull Object value,
            @Nullable Instant convergedAt) {
        if (desiredSequence <= 0) {
            throw new IllegalArgumentException("desired sequence 必须为正数");
        }
        this.propertyCode = TextUtils.requireText(propertyCode, "desired 属性 code");
        this.desiredSequence = desiredSequence;
        this.value = Objects.requireNonNull(value, "desired 值不能为空");
        this.convergedAt = convergedAt;
    }

    /** @return 属性 code */
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return 最近修改序号 */
    public long desiredSequence() {
        return desiredSequence;
    }

    /** @return 已按物模型解码的当前值 */
    public @NotNull Object value() {
        return value;
    }

    /** @return 最近一次收敛时间；尚未收敛时为空 */
    public @NotNull Optional<Instant> convergedAt() {
        return Optional.ofNullable(convergedAt);
    }
}
