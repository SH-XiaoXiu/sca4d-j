package com.sca4d.iot.timescale.persistence;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 映射一条 TimescaleDB Telemetry 查询结果。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class EncodedTelemetrySample {

    private final @NotNull String propertyCode;

    private final @NotNull String valueJson;

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    /** 创建 Telemetry 查询结果。 */
    public EncodedTelemetrySample(
            @NotNull String propertyCode,
            @NotNull String valueJson,
            @NotNull Instant observedAt,
            @NotNull Instant receivedAt) {
        this.propertyCode = Objects.requireNonNull(propertyCode, "Telemetry property code 不能为空");
        this.valueJson = Objects.requireNonNull(valueJson, "Telemetry value JSON 不能为空");
        this.observedAt = Objects.requireNonNull(observedAt, "Telemetry 观测时间不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "Telemetry 接收时间不能为空");
    }

    /** @return 属性 code */
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return 值 JSON */
    public @NotNull String valueJson() {
        return valueJson;
    }

    /** @return 观测时间 */
    public @NotNull Instant observedAt() {
        return observedAt;
    }

    /** @return 接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
