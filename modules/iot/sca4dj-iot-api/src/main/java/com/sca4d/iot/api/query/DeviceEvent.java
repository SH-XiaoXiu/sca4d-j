package com.sca4d.iot.api.query;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 保存一条已经持久化的离散设备事件。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class DeviceEvent {

    private final long eventId;

    private final long deviceId;

    private final @NotNull String eventCode;

    private final @NotNull Map<String, Object> outputs;

    private final @NotNull Instant occurredAt;

    private final @NotNull Instant receivedAt;

    /** 创建设备事件查询结果。 */
    public DeviceEvent(
            long eventId,
            long deviceId,
            @NotNull String eventCode,
            @NotNull Map<String, Object> outputs,
            @NotNull Instant occurredAt,
            @NotNull Instant receivedAt) {
        if (eventId <= 0 || deviceId <= 0) {
            throw new IllegalArgumentException("设备事件标识必须为正数");
        }
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.eventCode = TextUtils.requireText(eventCode, "设备事件 code");
        this.outputs = Map.copyOf(Objects.requireNonNull(outputs, "设备事件输出不能为空"));
        this.occurredAt = Objects.requireNonNull(occurredAt, "设备事件发生时间不能为空");
        this.receivedAt = Objects.requireNonNull(receivedAt, "设备事件接收时间不能为空");
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

    /** @return 不可修改事件输出 */
    public @NotNull @Unmodifiable Map<String, Object> outputs() {
        return outputs;
    }

    /** @return 设备或平台确定的事件发生时间 */
    public @NotNull Instant occurredAt() {
        return occurredAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant receivedAt() {
        return receivedAt;
    }
}
