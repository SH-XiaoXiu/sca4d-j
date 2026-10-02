package com.sca4d.app.server.iot.view;

import com.sca4d.iot.api.telemetry.TelemetrySample;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回一个物模型属性的高频时序样本。 */
@SuppressWarnings("unused")
public class IotTelemetryView {

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull Object value;

    private final @NotNull String propertyCode;

    /** 从模块 Telemetry 查询结果创建 HTTP 视图。 */
    private IotTelemetryView(@NotNull TelemetrySample sample) {
        this.propertyCode = sample.propertyCode();
        this.value = sample.value();
        this.observedAt = sample.observedAt();
        this.receivedAt = sample.receivedAt();
    }

    /** @return Telemetry HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotTelemetryView from(@NotNull TelemetrySample sample) {
        return new IotTelemetryView(Objects.requireNonNull(sample, "Telemetry 样本不能为空"));
    }

    /** @return 属性 code */
    public @NotNull String getPropertyCode() {
        return propertyCode;
    }

    /** @return 已按物模型解码的值 */
    public @NotNull Object getValue() {
        return value;
    }

    /** @return 设备或平台观测时间 */
    public @NotNull Instant getObservedAt() {
        return observedAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant getReceivedAt() {
        return receivedAt;
    }
}
