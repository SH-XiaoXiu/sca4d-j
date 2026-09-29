package com.sca4d.audit.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.audit.config.AuditProperties;
import com.sca4d.audit.payload.AuditPayloadJson;
import com.sca4d.audit.payload.AuditPayloadRegistry;
import com.sca4d.audit.recording.mapper.AuditMapper;
import com.sca4d.audit.recording.mapper.EncodedAuditEntry;
import com.sca4d.core.api.audit.AuditActor;
import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.name.Name;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

/** 验证可靠审计在交接前冻结数据、报告交接失败，并用消息标识消费。 */
class QueuedAuditRecorderTest {
    private final ObjectMapper json = new ObjectMapper();

    private final AuditMapper mapper = mock(AuditMapper.class);

    private final AuditRepository repository = new AuditRepository(
            mapper,
            () -> Id.of(99),
            new AuditPayloadJson(json, new AuditPayloadRegistry(List.of()), new AuditProperties()),
            "origin-instance");

    @Test
    @DisplayName("交接前冻结并脱敏，原 payload 变化不改变排队记录")
    void freezesAndRedactsBeforeHandoff() {
        MessageBus bus = mock(MessageBus.class);
        when(bus.dispatch(any(), any()))
                .thenReturn(new DispatchReceipt(Id.of(42), DispatchReceipt.Status.OUTBOX_ENLISTED));
        Payload payload = new Payload();
        new QueuedAuditRecorder(repository, bus).record(record(payload));
        payload.name = "changed";
        ArgumentCaptor<AuditMessage> message = ArgumentCaptor.forClass(AuditMessage.class);
        ArgumentCaptor<DispatchPolicy> policy = ArgumentCaptor.forClass(DispatchPolicy.class);
        verify(bus).dispatch(message.capture(), policy.capture());
        assertThat(policy.getValue().mode()).isEqualTo(DispatchPolicy.Mode.OUTBOX);
        AuditMessageCodec codec = new AuditMessageCodec(json);
        byte[] wire = codec.encode(message.getValue());
        assertThat(new String(wire, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("secret-value", "changed");
        AuditMessage restored = codec.decode(wire);
        assertThat(restored.entry().payloadJson()).contains("original", "***");
        assertThat(restored.entry().instanceId()).isEqualTo("origin-instance");
        assertThat(restored.entry().occurredAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("可靠交接失败原样向调用方报告，不伪装为审计完成")
    void reportsHandoffFailure() {
        MessageBus bus = mock(MessageBus.class);
        IllegalStateException failure = new IllegalStateException("outbox unavailable");
        when(bus.dispatch(any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> new QueuedAuditRecorder(repository, bus).record(record(new Payload())))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("消费使用消息 ID 作为主键，Mapper 失败不确认消息")
    void storesWithDeliveryIdAndPropagatesStorageFailure() {
        AuditMessage message = new AuditMessage(repository.freeze(record(new Payload())));
        var incoming = new IncomingMessage<>(Id.of(42), message, new Name("test"), Instant.EPOCH, false, 1);
        AuditMessageHandler handler = new AuditMessageHandler(repository, new AuditMessageCodec(json));
        handler.handle(incoming);
        ArgumentCaptor<EncodedAuditEntry> entry = ArgumentCaptor.forClass(EncodedAuditEntry.class);
        verify(mapper).insertDelivery(entry.capture());
        assertThat(entry.getValue().id()).isEqualTo(42);
        when(mapper.insertDelivery(any())).thenThrow(new IllegalStateException("database down"));
        assertThatThrownBy(() -> handler.handle(incoming)).isInstanceOf(IllegalStateException.class);
    }

    /** 创建固定操作及时间，确保异步消费者无需读取 Call。 */
    private static AuditRecord record(Payload payload) {
        return new AuditRecord(
                "call",
                new AuditActor(1, 2L, 3),
                new AuditOperation("TEST/UPDATE"),
                null,
                AuditResult.SUCCESS,
                payload,
                null,
                Instant.EPOCH);
    }

    /** 允许测试在交接后改变对象，验证消息不保留它的引用。 */
    public static class Payload implements AuditPayload {
        private String name = "original";

        public String getName() {
            return name;
        }

        public String getPassword() {
            return "secret-value";
        }
    }
}
