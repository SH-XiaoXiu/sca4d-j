package com.sca4d.iot.api.history;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 保存已经通过物模型校验、准备由 `MessageBus` 发布的一次 Device 事件。 */
public class DeviceEventMessage {

    private final @NotNull Instant occurredAt;

    private final @NotNull Instant receivedAt;

    private final @NotNull Map<String, Object> data;

    private final @NotNull String eventCode;

    private final @Nullable String sourceEventId;

    private final boolean deviceOccurredAt;

    private final long bindingGeneration;

    private final long deviceId;

    /** 创建一条不包含消息代理投递信息的 Device 事件。 */
    @JsonCreator
    public DeviceEventMessage(
            @JsonProperty("deviceId") long deviceId,
            @JsonProperty("bindingGeneration") long bindingGeneration,
            @JsonProperty("eventCode") @NotNull String eventCode,
            @JsonProperty("sourceEventId") @Nullable String sourceEventId,
            @JsonProperty("data") @NotNull Map<String, ?> data,
            @JsonProperty("occurredAt") @NotNull Instant occurredAt,
            @JsonProperty("deviceOccurredAt") boolean deviceOccurredAt,
            @JsonProperty("receivedAt") @NotNull Instant receivedAt) {
        if (deviceId <= 0 || bindingGeneration <= 0) {
            throw new IllegalArgumentException("Device 和绑定代数 必须为正数");
        }
        this.deviceId = deviceId;
        this.bindingGeneration = bindingGeneration;
        this.eventCode = TextUtils.requireText(eventCode, "Device 事件 code");
        this.sourceEventId = optionalSourceEventId(sourceEventId);
        this.data = Map.copyOf(Objects.requireNonNull(data, "Device 事件数据不能为空"));
        this.occurredAt = Objects.requireNonNull(occurredAt, "Device 事件发生时间不能为空");
        this.deviceOccurredAt = deviceOccurredAt;
        this.receivedAt = Objects.requireNonNull(receivedAt, "Device 事件接收时间不能为空");
    }

    /** @return 产生事件的 Device 标识 */
    @JsonProperty("deviceId")
    public long deviceId() {
        return deviceId;
    }

    /** @return 事件发生时的 Device 绑定版本 */
    @JsonProperty("bindingGeneration")
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /** @return 物模型或 IoT 系统事件编码 */
    @JsonProperty("eventCode")
    public @NotNull String eventCode() {
        return eventCode;
    }

    /** @return Device 提供的稳定事件标识；平台事件或不要求来源去重时为空 */
    @JsonProperty("sourceEventId")
    public @Nullable String sourceEventId() {
        return sourceEventId;
    }

    /** @return 不可修改的事件数据 */
    @JsonProperty("data")
    public @NotNull @Unmodifiable Map<String, Object> data() {
        return data;
    }

    /** @return Device 或平台确定的事件发生时间 */
    @JsonProperty("occurredAt")
    public @NotNull Instant occurredAt() {
        return occurredAt;
    }

    /** @return `occurredAt` 由 Device 或稳定来源提供时为 `true` */
    @JsonProperty("deviceOccurredAt")
    public boolean deviceOccurredAt() {
        return deviceOccurredAt;
    }

    /** @return 平台接收事件的时间 */
    @JsonProperty("receivedAt")
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }

    /** 校验可写入事件历史的来源事实标识。 */
    private static @Nullable String optionalSourceEventId(@Nullable String sourceEventId) {
        if (sourceEventId == null) {
            return null;
        }
        String checked = TextUtils.requireText(sourceEventId, "Device 来源事件标识");
        if (checked.length() > 256) {
            throw new IllegalArgumentException("Device 来源事件标识不能超过 256 个字符");
        }
        return checked;
    }
}
