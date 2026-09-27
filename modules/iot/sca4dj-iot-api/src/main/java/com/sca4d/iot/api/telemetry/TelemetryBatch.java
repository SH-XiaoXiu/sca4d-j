package com.sca4d.iot.api.telemetry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 保存一次 reported 报文中需要发布到消息代理的一批 Telemetry 样本。 */
@SuppressWarnings("ClassCanBeRecord")
public class TelemetryBatch {

    private final long deviceId;

    private final long bindingGeneration;

    private final @NotNull List<TelemetrySample> samples;

    /**
     * @param deviceId Device 标识
     * @param bindingGeneration 物模型绑定代数
     * @param samples 非空样本；消费端使用 Id 和样本顺序幂等写入
     */
    @JsonCreator
    public TelemetryBatch(
            @JsonProperty("deviceId") long deviceId,
            @JsonProperty("bindingGeneration") long bindingGeneration,
            @JsonProperty("samples") @NotNull List<TelemetrySample> samples) {
        if (deviceId <= 0 || bindingGeneration <= 0) {
            throw new IllegalArgumentException("Telemetry Device 和绑定代数 必须为正数");
        }
        this.deviceId = deviceId;
        this.bindingGeneration = bindingGeneration;
        this.samples = List.copyOf(Objects.requireNonNull(samples, "Telemetry 样本不能为空"));
        if (this.samples.isEmpty()) {
            throw new IllegalArgumentException("TelemetryBatch 必须至少包含一个样本");
        }
    }

    /**
     * @return Device 标识
     */
    @JsonProperty("deviceId")
    public long deviceId() {
        return deviceId;
    }

    /**
     * @return 物模型绑定代数
     */
    @JsonProperty("bindingGeneration")
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /**
     * @return 不可修改样本
     */
    @JsonProperty("samples")
    public @NotNull @Unmodifiable List<TelemetrySample> samples() {
        return samples;
    }
}
