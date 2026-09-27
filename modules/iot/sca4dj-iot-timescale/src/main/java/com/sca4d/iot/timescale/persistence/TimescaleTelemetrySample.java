package com.sca4d.iot.timescale.persistence;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存一次消息消费批量写入 TimescaleDB 所需的单条 Telemetry 样本字段。 */
@SuppressWarnings({"ClassCanBeRecord", "unused"}) // MyBatis XML 通过属性名读取写入参数。
public class TimescaleTelemetrySample {

    private final long messageId;

    private final int sampleIndex;

    private final long deviceId;

    private final long bindingGeneration;

    private final @NotNull String propertyCode;

    private final @NotNull String valueJson;

    private final @NotNull Instant observedAt;

    private final @NotNull Instant receivedAt;

    /** 创建已经完成物模型值编码的 Telemetry 持久化字段。 */
    public TimescaleTelemetrySample(
            long messageId,
            int sampleIndex,
            long deviceId,
            long bindingGeneration,
            @NotNull String propertyCode,
            @NotNull String valueJson,
            @NotNull Instant observedAt,
            @NotNull Instant receivedAt) {
        if (messageId <= 0 || sampleIndex < 0 || deviceId <= 0 || bindingGeneration <= 0) {
            throw new IllegalArgumentException("Telemetry Id、样本序号、Device 或绑定版本不合法");
        }
        this.messageId = messageId;
        this.sampleIndex = sampleIndex;
        this.deviceId = deviceId;
        this.bindingGeneration = bindingGeneration;
        this.propertyCode = Objects.requireNonNull(propertyCode, "Telemetry property code 不能为空");
        this.valueJson = Objects.requireNonNull(valueJson, "Telemetry value JSON 不能为空");
        this.observedAt = Objects.requireNonNull(observedAt, "Telemetry observedAt 不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "Telemetry receivedAt 不能为空");
    }

    /** @return 通用 Messaging 分配的稳定消息标识 */
    public long messageId() {
        return messageId;
    }

    /** @return 样本在消息内的稳定序号 */
    public int sampleIndex() {
        return sampleIndex;
    }

    /** @return Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return Device 物模型绑定代数 */
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /** @return property code */
    public @NotNull String propertyCode() {
        return propertyCode;
    }

    /** @return 已规范化的物模型值 JSON */
    public @NotNull String valueJson() {
        return valueJson;
    }

    /** @return 设备或协议声明的观测时间 */
    public @NotNull Instant observedAt() {
        return observedAt;
    }

    /** @return Sca4d-J 接受样本的时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
