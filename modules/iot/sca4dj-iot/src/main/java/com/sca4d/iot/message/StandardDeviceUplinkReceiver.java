package com.sca4d.iot.message;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceUplinkReceiver;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.api.state.ThingEventOccurrence;
import com.sca4d.iot.state.ReportedStateService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 把协议标准上行结果交给 IoT 的 reported、事件和回执业务事务。 */
public class StandardDeviceUplinkReceiver implements DeviceUplinkReceiver {

    private final @NotNull ReportedStateService reportedStates;

    private final @NotNull DeviceAcknowledgementService acknowledgements;

    /** 创建 IoT 模块对设备接入实现公开的上行接收入口。 */
    public StandardDeviceUplinkReceiver(
            @NotNull ReportedStateService reportedStates,
            @NotNull DeviceAcknowledgementService acknowledgements) {
        this.reportedStates = Objects.requireNonNull(reportedStates, "ReportedStateService 不能为空");
        this.acknowledgements = Objects.requireNonNull(
                acknowledgements,
                "DeviceAcknowledgementService 不能为空");
    }

    /** reported state 和同一报文产生的事件由 `ReportedStateService` 在一个事务中接受。 */
    @Override
    public void reported(
            @NotNull Device device,
            @NotNull ReportedStateUpdate update,
            @NotNull List<ThingEventOccurrence> events) {
        reportedStates.accept(device, update, events);
    }

    /** 设备显式回执由 `DeviceAcknowledgementService` 校验消息归属并执行状态转换。 */
    @Override
    public void acknowledged(@NotNull Device device, @NotNull DeviceAcknowledgement acknowledgement) {
        acknowledgements.acknowledge(
                Objects.requireNonNull(device, "Device 不能为空").id(),
                acknowledgement);
    }
}
