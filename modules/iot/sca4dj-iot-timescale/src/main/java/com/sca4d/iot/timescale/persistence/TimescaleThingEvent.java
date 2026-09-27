package com.sca4d.iot.timescale.persistence;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存 TimescaleDB 查询返回的一条已编码 ThingEvent。 */
public class TimescaleThingEvent {

    private final long eventId;

    private final long deviceId;

    private final @NotNull String eventCode;

    private final @Nullable String sourceEventId;

    private final @NotNull String outputJson;

    private final @NotNull Instant occurredAt;

    private final @NotNull Instant receivedAt;

    /** 创建 TimescaleDB ThingEvent 查询结果。 */
    public TimescaleThingEvent(
            long eventId,
            long deviceId,
            @NotNull String eventCode,
            @Nullable String sourceEventId,
            @NotNull String outputJson,
            @NotNull Instant occurredAt,
            @NotNull Instant receivedAt) {
        if (eventId <= 0 || deviceId <= 0) {
            throw new IllegalArgumentException("TimescaleDB 事件和 Device 标识必须为正数");
        }
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.eventCode = Objects.requireNonNull(eventCode, "ThingEvent code 不能为空");
        this.sourceEventId = sourceEventId;
        this.outputJson = Objects.requireNonNull(outputJson, "ThingEvent output JSON 不能为空");
        this.occurredAt = Objects.requireNonNull(occurredAt, "ThingEvent 发生时间不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "ThingEvent 接收时间不能为空");
    }

    /** @return 平台事件标识 */
    public long eventId() {
        return eventId;
    }

    /** @return Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return 物模型事件 code */
    public @NotNull String eventCode() {
        return eventCode;
    }

    /** @return 可选来源事件标识 */
    public @Nullable String sourceEventId() {
        return sourceEventId;
    }

    /** @return 事件输出 JSON */
    public @NotNull String outputJson() {
        return outputJson;
    }

    /** @return 事件发生时间 */
    public @NotNull Instant occurredAt() {
        return occurredAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
