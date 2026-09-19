package com.sca4d.iot.api.message;

import com.sca4d.core.api.event.EventType;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import org.jetbrains.annotations.NotNull;

/** 声明 IoT 模块发布和消费的稳定消息类型。 */
public final class IotMessageTypes {

    /** reported 中标记为 Telemetry 的属性样本批次。 */
    public static final @NotNull EventType<TelemetryBatch> TELEMETRY_REPORTED = new EventType<>(
            "iot.telemetry.reported",
            1,
            TelemetryBatch.class);

    /** 物模型或 IoT 状态机产生的离散 Device 事件。 */
    public static final @NotNull EventType<DeviceEventMessage> DEVICE_EVENT_OCCURRED = new EventType<>(
            "iot.device.event.occurred",
            1,
            DeviceEventMessage.class);

    /** 请求协议实现执行一次标准 Device 下行发送。 */
    public static final @NotNull EventType<DeviceDownlinkDispatch> DOWNLINK_DISPATCH = new EventType<>(
            "iot.downlink.dispatch",
            1,
            DeviceDownlinkDispatch.class);

    /** 检查一次已经发布的标准 Device 下行是否等待 ACK 超时。 */
    public static final @NotNull EventType<DeviceAcknowledgementTimeout> ACKNOWLEDGEMENT_TIMEOUT = new EventType<>(
            "iot.downlink.ack-timeout",
            1,
            DeviceAcknowledgementTimeout.class);

    /** 请求在来源事务提交后执行一次业务 ACK 状态转换。 */
    public static final @NotNull EventType<DeviceAcknowledgement> ACKNOWLEDGEMENT_REQUESTED = new EventType<>(
            "iot.downlink.ack-requested",
            1,
            DeviceAcknowledgement.class);

    /** 请求协议实现终止一个明确的 Device 连接代次。 */
    public static final @NotNull EventType<DeviceDisconnectMessage> DEVICE_DISCONNECT = new EventType<>(
            "iot.device.disconnect",
            1,
            DeviceDisconnectMessage.class);

    /** 禁止实例化只声明稳定消息类型的类。 */
    private IotMessageTypes() {
    }
}
