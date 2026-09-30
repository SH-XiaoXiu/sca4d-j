package com.sca4d.iot.api.telemetry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存一个物模型属性在明确采样时间产生的时序值。 */
public class TelemetrySample {

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull Object value;

    private final @NotNull String propertyCode;

    /** @param propertyCode 属性 code @param value 已按物模型校验的值 @param observedAt 设备或平台观测时间 @param receivedAt 平台接收时间 */
    @JsonCreator
    public TelemetrySample(
            @JsonProperty("propertyCode") @NotNull String propertyCode,
            @JsonProperty("value") @NotNull Object value,
            @JsonProperty("observedAt") @NotNull Instant observedAt,
            @JsonProperty("receivedAt") @NotNull Instant receivedAt) {
        this.propertyCode = TextUtils.requireText(propertyCode, "Telemetry 属性 code");
        this.value = Objects.requireNonNull(value, "Telemetry 值不能为空");
        this.observedAt = Objects.requireNonNull(observedAt, "Telemetry 观测时间不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "Telemetry 接收时间不能为空");
    }

    /** @return 属性 code */
    @JsonProperty("propertyCode")
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return 已校验 Java 值 */
    @JsonProperty("value")
    public @NotNull Object value() {
        return value;
    }

    /** @return 设备或平台观测时间 */
    @JsonProperty("observedAt")
    public @NotNull Instant observedAt() {
        return observedAt;
    }

    /** @return 平台接收时间 */
    @JsonProperty("receivedAt")
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
