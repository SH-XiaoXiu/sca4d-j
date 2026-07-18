package com.sca4d.common.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证审计内容类型注册、重复检测和类型查找。 */
class AuditPayloadRegistryTest {

    @Test
    void shouldRejectNullPayloadTypeFromExtension() {
        @SuppressWarnings("unchecked")
        AuditPayloadCodec<RawAuditPayload> codec = mock(AuditPayloadCodec.class);
        when(codec.payloadType()).thenReturn(null);

        assertThatThrownBy(() -> new AuditPayloadRegistry(List.of(codec)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("审计载荷 Codec 类型不能为空");
    }

    @Test
    void shouldRejectMultipleCodecsForSamePayloadClass() {
        @SuppressWarnings("unchecked")
        AuditPayloadCodec<RawAuditPayload> first = mock(AuditPayloadCodec.class);
        @SuppressWarnings("unchecked")
        AuditPayloadCodec<RawAuditPayload> second = mock(AuditPayloadCodec.class);
        when(first.payloadType()).thenReturn("first");
        when(first.payloadVersion()).thenReturn(1);
        when(first.payloadClass()).thenReturn(RawAuditPayload.class);
        when(second.payloadType()).thenReturn("second");
        when(second.payloadVersion()).thenReturn(1);
        when(second.payloadClass()).thenReturn(RawAuditPayload.class);

        assertThatThrownBy(() -> new AuditPayloadRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RawAuditPayload.class.getName());
    }

    @Test
    void shouldRejectPayloadTypeWithSurroundingWhitespace() {
        @SuppressWarnings("unchecked")
        AuditPayloadCodec<RawAuditPayload> codec = mock(AuditPayloadCodec.class);
        when(codec.payloadType()).thenReturn(" raw ");
        when(codec.payloadVersion()).thenReturn(1);
        when(codec.payloadClass()).thenReturn(RawAuditPayload.class);

        assertThatThrownBy(() -> new AuditPayloadRegistry(List.of(codec)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("首尾空白");
    }
}
