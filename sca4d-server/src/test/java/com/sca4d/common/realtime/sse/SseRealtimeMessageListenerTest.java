package com.sca4d.common.realtime.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.messaging.MessageEnvelope;
import com.sca4d.common.realtime.RealtimeEnvelope;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/** 验证 RabbitMQ 实时消息只发送给本节点已注册的 SSE 连接。 */
class SseRealtimeMessageListenerTest {

    @Test
    void shouldDeliverTypedRealtimeEnvelopeByRoutingKey() {
        SseConnectionRegistry registry = mock(SseConnectionRegistry.class);
        SseRealtimeMessageListener listener = new SseRealtimeMessageListener(registry);
        RealtimeEnvelope<Object> realtimeEnvelope = new RealtimeEnvelope<>("notification.created", Map.of("id", 7L), Map.of());
        MessageEnvelope<RealtimeEnvelope<Object>> messageEnvelope = new MessageEnvelope<RealtimeEnvelope<Object>>().setPayload(realtimeEnvelope);
        Message<MessageEnvelope<RealtimeEnvelope<Object>>> message = MessageBuilder.withPayload(messageEnvelope)
                .setHeader(AmqpHeaders.RECEIVED_ROUTING_KEY, "subject.7")
                .build();

        listener.onMessage(message);

        verify(registry).send("subject.7", realtimeEnvelope);
    }

    @Test
    void shouldIgnoreMessageWithoutRoutingKey() {
        SseConnectionRegistry registry = mock(SseConnectionRegistry.class);
        SseRealtimeMessageListener listener = new SseRealtimeMessageListener(registry);
        MessageEnvelope<RealtimeEnvelope<Object>> messageEnvelope = new MessageEnvelope<RealtimeEnvelope<Object>>()
                .setPayload(RealtimeEnvelope.of("notification.created", Map.of("id", 7L)));

        listener.onMessage(MessageBuilder.withPayload(messageEnvelope).build());

        verifyNoInteractions(registry);
    }

    @Test
    void shouldRejectMessageWithoutRealtimePayload() {
        SseRealtimeMessageListener listener = new SseRealtimeMessageListener(mock(SseConnectionRegistry.class));
        Message<MessageEnvelope<RealtimeEnvelope<Object>>> message = MessageBuilder
                .withPayload(new MessageEnvelope<RealtimeEnvelope<Object>>())
                .setHeader(AmqpHeaders.RECEIVED_ROUTING_KEY, "subject.7")
                .build();

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(InternalServiceException.class)
                .hasMessage("SSE 消息缺少实时载荷");
    }

    @Test
    void shouldConvertStandardMessageEnvelopeFromJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        RealtimeEnvelope<Object> realtimeEnvelope = new RealtimeEnvelope<>("notification.created", Map.of("id", 7L), Map.of());
        MessageEnvelope<RealtimeEnvelope<Object>> messageEnvelope = new MessageEnvelope<RealtimeEnvelope<Object>>()
                .setMessageId("message-7")
                .setEvent(realtimeEnvelope.getEvent())
                .setSource("sca4d-server")
                .setPayload(realtimeEnvelope);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(listenerPayloadType());
        org.springframework.amqp.core.Message message = new org.springframework.amqp.core.Message(
                objectMapper.writeValueAsBytes(messageEnvelope), properties);

        Object converted = converter.fromMessage(message);

        assertThat(converted).isInstanceOf(MessageEnvelope.class);
        MessageEnvelope<?> convertedEnvelope = (MessageEnvelope<?>) converted;
        assertThat(convertedEnvelope.getMessageId()).isEqualTo("message-7");
        assertThat(convertedEnvelope.getPayload()).isInstanceOf(RealtimeEnvelope.class);
        assertThat(((RealtimeEnvelope<?>) convertedEnvelope.getPayload()).getEvent())
                .isEqualTo("notification.created");
    }

    @Test
    void shouldDeclareAnonymousExclusiveAutoDeleteQueueBinding() throws Exception {
        RabbitListener listener = SseRealtimeMessageListener.class
                .getMethod("onMessage", Message.class)
                .getAnnotation(RabbitListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.messageConverter()).isEmpty();
        assertThat(listener.bindings()).hasSize(1);
        QueueBinding binding = listener.bindings()[0];
        assertThat(binding.value().value()).isEmpty();
        assertThat(binding.value().durable()).isEqualTo("false");
        assertThat(binding.value().exclusive()).isEqualTo("true");
        assertThat(binding.value().autoDelete()).isEqualTo("true");
        assertThat(binding.exchange().value())
                .isEqualTo("${sca4dj.realtime.sse.exchange:sca4dj.realtime.sse}");
        assertThat(binding.exchange().type()).isEqualTo(ExchangeTypes.TOPIC);
        assertThat(binding.key()).containsExactly("#");
    }

    /**
     * 读取 SSE Rabbit 监听方法声明的消息载荷泛型，用于验证共用消息转换器。
     *
     * @return {@code MessageEnvelope<RealtimeEnvelope<Object>>} 对应的反射类型
     * @throws NoSuchMethodException 监听方法契约发生漂移时抛出
     */
    private @NotNull Type listenerPayloadType() throws NoSuchMethodException {
        Type messageType = SseRealtimeMessageListener.class
                .getMethod("onMessage", Message.class)
                .getGenericParameterTypes()[0];
        return ((ParameterizedType) messageType).getActualTypeArguments()[0];
    }
}
