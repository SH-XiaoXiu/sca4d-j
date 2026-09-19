package com.sca4d.iot.protocol;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceUplink;
import com.sca4d.iot.api.message.DeviceUplinkReceiver;
import com.sca4d.iot.api.protocol.DeviceProtocolMessage;
import com.sca4d.iot.api.protocol.DeviceUplinkProtocol;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证传输路由不选择设备协议，统一分发器只按 Device 台账选择实际协议。 */
class StandardDeviceUplinkDispatcherTest {

    @Test
    @DisplayName("按 Device protocolCode 解码并提交标准 reported")
    void dispatchesWithProtocolSelectedFromDevice() {
        Instant observedAt = Instant.parse("2026-08-15T05:00:00Z");
        Device device = new Device(
                91,
                ProductBinding.code("relay-sensor"),
                "sensor-91",
                null,
                "中继传感器 91",
                null,
                1,
                1,
                "relay-sensor-v1",
                null,
                Set.of());
        DeviceProtocolMessage message = new DeviceProtocolMessage(
                device,
                "status-frame",
                new byte[] {0x01},
                observedAt);
        ReportedStateUpdate update = new ReportedStateUpdate(
                "status",
                ReportedMergeMode.PATCH,
                Set.of("online"),
                Map.of("online", true),
                null,
                null,
                observedAt);
        DeviceUplinkProtocol protocol = mock(DeviceUplinkProtocol.class);
        when(protocol.code()).thenReturn("relay-sensor-v1");
        when(protocol.decode(device, message)).thenReturn(DeviceUplink.reported(update, List.of()));
        DeviceUplinkReceiver receiver = mock(DeviceUplinkReceiver.class);
        StandardDeviceUplinkDispatcher dispatcher = new StandardDeviceUplinkDispatcher(
                new DeviceProtocolCatalog(List.of(protocol)),
                receiver);

        dispatcher.dispatch(message);

        verify(protocol).decode(device, message);
        verify(receiver).reported(device, update, List.of());
    }

    @Test
    @DisplayName("同一设备上行同时提交 reported 和 acknowledgement")
    void dispatchesReportedStateAndAcknowledgementFromOneMessage() {
        Instant observedAt = Instant.parse("2026-08-15T05:00:00Z");
        Device device = new Device(
                92,
                ProductBinding.code("relay-sensor"),
                "sensor-92",
                null,
                "中继传感器 92",
                null,
                1,
                1,
                "relay-sensor-v1",
                null,
                Set.of());
        DeviceProtocolMessage message = new DeviceProtocolMessage(
                device,
                "command-result-frame",
                new byte[] {0x02},
                observedAt);
        ReportedStateUpdate update = new ReportedStateUpdate(
                "status",
                ReportedMergeMode.PATCH,
                Set.of("online"),
                Map.of("online", true),
                null,
                null,
                observedAt);
        DeviceAcknowledgement acknowledgement = new DeviceAcknowledgement(
                101,
                "success",
                Map.of(),
                "DEVICE",
                "result-101",
                observedAt,
                observedAt);
        DeviceUplinkProtocol protocol = mock(DeviceUplinkProtocol.class);
        when(protocol.code()).thenReturn("relay-sensor-v1");
        when(protocol.decode(device, message))
                .thenReturn(
                        DeviceUplink.reportedAndAcknowledged(
                                update,
                                List.of(),
                                acknowledgement));
        DeviceUplinkReceiver receiver = mock(DeviceUplinkReceiver.class);
        StandardDeviceUplinkDispatcher dispatcher = new StandardDeviceUplinkDispatcher(
                new DeviceProtocolCatalog(List.of(protocol)),
                receiver);

        dispatcher.dispatch(message);

        verify(receiver).reported(device, update, List.of());
        verify(receiver).acknowledged(device, acknowledgement);
    }
}
