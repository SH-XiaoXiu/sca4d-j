package com.sca4d.common.realtime.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeTarget;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/** 验证 SSE 连接注册、定向发送、过期清理和并发关闭。 */
class SseConnectionRegistryTest {

    @Test
    void shouldSendToLocalConnectionsByTarget() throws Exception {
        SseConnectionRegistry registry = new SseConnectionRegistry();
        RealtimeTarget target = RealtimeTarget.subject(7L);
        SseEmitter emitter = mock(SseEmitter.class);

        String connectionId = registry.register(emitter, List.of(target));

        assertThat(connectionId).isNotBlank();
        registry.send(target.routingKey(), RealtimeEnvelope.of("notification.created", "data"));

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));

        registry.remove(connectionId);
        registry.send(target.routingKey(), RealtimeEnvelope.of("notification.created", "data"));

        verifyNoMoreInteractions(emitter);
    }

    @Test
    void shouldCompleteAndRemoveConnectionWhenMessageSendFails() throws Exception {
        SseConnectionRegistry registry = new SseConnectionRegistry();
        RealtimeTarget target = RealtimeTarget.subject(7L);
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("client disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        registry.register(emitter, List.of(target));

        registry.send(target.routingKey(), RealtimeEnvelope.of("notification.created", "data"));
        registry.send(target.routingKey(), RealtimeEnvelope.of("notification.created", "data"));

        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any());
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldCompleteAndRemoveConnectionWhenHeartbeatFails() throws Exception {
        SseConnectionRegistry registry = new SseConnectionRegistry();
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("client disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        registry.register(emitter, List.of(RealtimeTarget.subject(7L)));

        registry.heartbeat();
        registry.heartbeat();

        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any());
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
