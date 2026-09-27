package com.sca4d.app.server.iot.view;

import com.sca4d.iot.api.query.DeviceEvent;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 返回一条已经持久化的离散设备事件。 */
@SuppressWarnings("unused")
public class IotDeviceEventView {

    private final long eventId;

    private final long deviceId;

    private final @NotNull String eventCode;

    private final @NotNull @Unmodifiable Map<String, Object> outputs;

    private final @NotNull Instant occurredAt;

    private final @NotNull Instant receivedAt;

    /** 从模块事件查询结果创建 HTTP 视图。 */
    private IotDeviceEventView(@NotNull DeviceEvent event) {
        this.eventId = event.eventId();
        this.deviceId = event.deviceId();
        this.eventCode = event.eventCode();
        this.outputs = event.outputs();
        this.occurredAt = event.occurredAt();
        this.receivedAt = event.receivedAt();
    }

    /** @return 设备事件 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotDeviceEventView from(@NotNull DeviceEvent event) {
        return new IotDeviceEventView(Objects.requireNonNull(event, "Device 事件不能为空"));
    }

    /** @return 平台事件标识 */
    public long getEventId() {
        return eventId;
    }

    /** @return Device 标识 */
    public long getDeviceId() {
        return deviceId;
    }

    /** @return 物模型事件 code */
    public @NotNull String getEventCode() {
        return eventCode;
    }

    /** @return 事件输出 */
    public @NotNull @Unmodifiable Map<String, Object> getOutputs() {
        return Map.copyOf(outputs);
    }

    /** @return 设备或平台确定的发生时间 */
    public @NotNull Instant getOccurredAt() {
        return occurredAt;
    }

    /** @return 平台接收时间 */
    public @NotNull Instant getReceivedAt() {
        return receivedAt;
    }
}
