package com.sca4d.iot.api.message;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.api.state.ThingEventOccurrence;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 接收设备协议已经转换为 IoT 标准对象的上行结果。 */
public interface DeviceUplinkReceiver {

    /**
     * 在一个业务事务中接受 reported state 及同一报文产生的 ThingEvent。
     *
     * @param device 已经由连接身份解析出的完整 Device
     * @param update reported 字段组更新
     * @param events 同一报文产生的离散事件；没有事件时传空列表
     */
    void reported(
            @NotNull Device device,
            @NotNull ReportedStateUpdate update,
            @NotNull List<ThingEventOccurrence> events);

    /**
     * 接受设备显式回执或其他接入事实转换出的业务确认。
     *
     * @param device 已经由连接身份解析出的完整 Device
     * @param acknowledgement 尚未分配数据库标识的标准业务确认
     */
    void acknowledged(@NotNull Device device, @NotNull DeviceAcknowledgement acknowledgement);
}
