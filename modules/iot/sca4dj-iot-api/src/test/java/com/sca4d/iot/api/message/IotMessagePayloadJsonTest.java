package com.sca4d.iot.api.message;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 验证 `IotMessageTypes` 声明的载荷能够由 Messaging 使用的 Jackson 契约双向编解码。 */
class IotMessagePayloadJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsDownlinkControlMessages() throws Exception {
        DeviceDownlinkDispatch dispatch = roundTrip(
                new DeviceDownlinkDispatch(101, 2),
                DeviceDownlinkDispatch.class);
        DeviceAcknowledgementTimeout timeout = roundTrip(
                new DeviceAcknowledgementTimeout(102, 3),
                DeviceAcknowledgementTimeout.class);

        assertAll(
                () -> assertEquals(101, dispatch.messageId()),
                () -> assertEquals(2, dispatch.attempt()),
                () -> assertEquals(102, timeout.messageId()),
                () -> assertEquals(3, timeout.attempt()));
    }

    @Test
    void roundTripsDeviceEventMessage() throws Exception {
        Instant occurredAt = Instant.parse("2026-08-15T12:16:17.947574025Z");
        DeviceEventMessage message = roundTrip(
                new DeviceEventMessage(
                        103,
                        4,
                        "status.reported",
                        "gateway-event-1",
                        Map.of("emergency", false),
                        occurredAt,
                        true,
                        occurredAt.plusMillis(5)),
                DeviceEventMessage.class);

        assertAll(
                () -> assertEquals(103, message.deviceId()),
                () -> assertEquals(4, message.bindingGeneration()),
                () -> assertEquals("status.reported", message.eventCode()),
                () -> assertEquals("gateway-event-1", message.sourceEventId()),
                () -> assertEquals(Map.of("emergency", false), message.data()),
                () -> assertEquals(occurredAt, message.occurredAt()),
                () -> assertEquals(true, message.deviceOccurredAt()),
                () -> assertEquals(occurredAt.plusMillis(5), message.receivedAt()));
    }

    @Test
    void roundTripsTelemetryBatch() throws Exception {
        Instant observedAt = Instant.parse("2026-08-15T12:17:18Z");
        TelemetryBatch batch = roundTrip(
                new TelemetryBatch(
                        104,
                        5,
                        List.of(
                                new TelemetrySample(
                                        "batteryPercent",
                                        100L,
                                        observedAt,
                                        observedAt.plusMillis(5)))),
                TelemetryBatch.class);

        assertAll(
                () -> assertEquals(104, batch.deviceId()),
                () -> assertEquals(5, batch.bindingGeneration()),
                () -> assertEquals(1, batch.samples().size()),
                () -> assertEquals("batteryPercent", batch.samples().getFirst().propertyCode()),
                () -> assertEquals(100, batch.samples().getFirst().value()),
                () -> assertEquals(observedAt, batch.samples().getFirst().observedAt()),
                () -> assertEquals(observedAt.plusMillis(5), batch.samples().getFirst().receivedAt()));
    }

    @Test
    void roundTripsDisconnectMessage() throws Exception {
        DeviceDisconnectMessage message = roundTrip(
                new DeviceDisconnectMessage(
                        105,
                        new DeviceConnectionId("connection-1"),
                        DeviceConnectionProtocol.MQTT,
                        "device-disabled",
                        2),
                DeviceDisconnectMessage.class);

        assertAll(
                () -> assertEquals(105, message.deviceId()),
                () -> assertEquals(new DeviceConnectionId("connection-1"), message.connectionId()),
                () -> assertEquals(DeviceConnectionProtocol.MQTT, message.protocol()),
                () -> assertEquals("device-disabled", message.reason()),
                () -> assertEquals(2, message.attempt()));
    }

    private <T> T roundTrip(Object source, Class<T> type) throws Exception {
        return mapper.readValue(mapper.writeValueAsBytes(source), type);
    }
}
