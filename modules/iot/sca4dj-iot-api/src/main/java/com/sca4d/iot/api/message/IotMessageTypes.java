package com.sca4d.iot.api.message;

import com.sca4d.core.api.event.EventType;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import org.jetbrains.annotations.NotNull;

/** 声明 IoT 模块发布和消费的消息类型。 */
public class IotMessageTypes {

    /** reported 中标记为 Telemetry 的属性样本批次。 */
    public static final @NotNull EventType<TelemetryBatch> TELEMETRY_REPORTED = new EventType<>(
            "IOT/TELEMETRY/REPORTED",
            1,
            TelemetryBatch.class);

    /** 物模型或 IoT 状态机产生的离散 Device 事件。 */
    public static final @NotNull EventType<DeviceEventMessage> DEVICE_EVENT_OCCURRED = new EventType<>(
            "IOT/DEVICE/EVENT/OCCURRED",
            1,
            DeviceEventMessage.class);

    /** 禁止实例化只声明稳定消息类型的类。 */
    private IotMessageTypes() {
    }
}
