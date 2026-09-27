package com.sca4d.iot.persistence;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 保存当前 desired property 的规范 JSON 和 desired sequence。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EncodedDesiredProperty {

    private final long deviceId;

    private final long bindingGeneration;

    private final @NotNull String propertyCode;

    private final long desiredSequence;

    private final @NotNull String valueJson;

    private final @Nullable Instant convergedAt;

    /** 创建已经完成 JSON 编码的 desired property。 */
    public EncodedDesiredProperty(
            long deviceId,
            long bindingGeneration,
            @NotNull String propertyCode,
            long desiredSequence,
            @NotNull String valueJson,
            @Nullable Instant convergedAt) {
        this.deviceId = deviceId;
        this.bindingGeneration = bindingGeneration;
        this.propertyCode = propertyCode;
        this.desiredSequence = desiredSequence;
        this.valueJson = valueJson;
        this.convergedAt = convergedAt;
    }

    /** @return Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return 物模型绑定代数 */
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /** @return 属性 code */
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return desired sequence */
    public long desiredSequence() {
        return desiredSequence;
    }

    /** @return 规范 JSON 值 */
    public @NotNull String valueJson() {
        return valueJson;
    }

    /** @return 最近收敛时间 */
    public @Nullable Instant convergedAt() {
        return convergedAt;
    }
}
