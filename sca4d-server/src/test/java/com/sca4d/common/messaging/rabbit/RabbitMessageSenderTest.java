package com.sca4d.common.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sca4d.common.messaging.MessageEnvelope;
import com.sca4d.common.messaging.MessagePublishException;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 验证 RabbitMQ 发送参数、消息头和异常转换。 */
class RabbitMessageSenderTest {

    @Test
    void shouldUseRabbitTemplateConverterAndApplyMessageProperties() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Sca4djMessagingProperties properties = new Sca4djMessagingProperties();
        properties.getRabbit().setDelayedExchangeEnabled(true);
        MessageEnvelope<Object> envelope = envelope();
        AtomicReference<Message> sentMessage = new AtomicReference<>();
        doAnswer(invocation -> {
            MessagePostProcessor postProcessor = invocation.getArgument(3);
            CorrelationData correlationData = invocation.getArgument(4);
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            Message converted = new Message("{\"payload\":{}}".getBytes(StandardCharsets.UTF_8), messageProperties);
            sentMessage.set(postProcessor.postProcessMessage(converted));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        })
                .when(rabbitTemplate)
                .convertAndSend(
                        eq("order.event"),
                        eq("order.created"),
                        same(envelope),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class));
        RabbitMessageSender sender = new RabbitMessageSender(rabbitTemplate, properties);

        sender.send("order.event", "order.created", envelope, Duration.ofSeconds(3));

        verify(rabbitTemplate).setMandatory(true);
        Message message = sentMessage.get();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"payload\":{}}");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo("message-7");
        assertThat(message.getMessageProperties().getType()).isEqualTo("order.created");
        assertThat(message.getMessageProperties().getTimestamp())
                .isEqualTo(Date.from(envelope.getPublishedAt().toInstant()));
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("custom", "value")
                .containsEntry("x-message-id", "message-7")
                .containsEntry("x-event", "order.created")
                .containsEntry("x-business-key", "order:7")
                .containsEntry("x-trace-id", "trace-7")
                .containsEntry("x-source", "sca4d-server")
                .containsEntry(MessageProperties.X_DELAY, 3000);
    }

    @Test
    void shouldRejectDelayWhenDelayedExchangeIsDisabled() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMessageSender sender = new RabbitMessageSender(rabbitTemplate, new Sca4djMessagingProperties());

        assertThatThrownBy(() -> sender.send("order.event", "order.created", envelope(), Duration.ofSeconds(1)))
                .isInstanceOf(MessagePublishException.class)
                .hasMessage("当前 RabbitMQ 未启用 delayed exchange，不能直接发送延迟消息");

        verify(rabbitTemplate, never())
                .convertAndSend(any(String.class), any(String.class), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void shouldReportMessageConversionFailure() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessageEnvelope<Object> envelope = envelope();
        doThrow(new MessageConversionException("invalid json"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq("order.event"),
                        eq("order.created"),
                        same(envelope),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class));
        RabbitMessageSender sender = new RabbitMessageSender(rabbitTemplate, new Sca4djMessagingProperties());

        assertThatThrownBy(() -> sender.send("order.event", "order.created", envelope, Duration.ZERO))
                .isInstanceOf(MessagePublishException.class)
                .hasMessage("消息信封序列化失败");
    }

    @Test
    void shouldReportNackAndUnroutableReturn() {
        RabbitTemplate nackTemplate = mock(RabbitTemplate.class);
        MessageEnvelope<Object> envelope = envelope();
        complete(nackTemplate, envelope, new CorrelationData.Confirm(false, "denied"), false);
        RabbitMessageSender nackSender = new RabbitMessageSender(nackTemplate, new Sca4djMessagingProperties());

        assertThatThrownBy(() -> nackSender.send("order.event", "order.created", envelope, Duration.ZERO))
                .isInstanceOf(MessagePublishException.class)
                .hasMessage("RabbitMQ confirm 返回 nack");

        RabbitTemplate returnedTemplate = mock(RabbitTemplate.class);
        complete(returnedTemplate, envelope, new CorrelationData.Confirm(true, null), true);
        RabbitMessageSender returnedSender = new RabbitMessageSender(returnedTemplate, new Sca4djMessagingProperties());

        assertThatThrownBy(() -> returnedSender.send("order.event", "order.created", envelope, Duration.ZERO))
                .isInstanceOf(MessagePublishException.class)
                .hasMessage("RabbitMQ 消息不可路由");
    }

    /** 配置 RabbitTemplate 在发送时完成 confirm，并按需附加不可路由返回消息。 */
    private void complete(
            RabbitTemplate rabbitTemplate,
            MessageEnvelope<Object> envelope,
            CorrelationData.Confirm confirm,
            boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            if (returned) {
                correlationData.setReturned(new ReturnedMessage(
                        new Message(new byte[0], new MessageProperties()),
                        312,
                        "NO_ROUTE",
                        "order.event",
                        "order.created"));
            }
            correlationData.getFuture().complete(confirm);
            return null;
        })
                .when(rabbitTemplate)
                .convertAndSend(
                        eq("order.event"),
                        eq("order.created"),
                        same(envelope),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class));
    }

    /** @return 包含固定消息头、追踪信息和测试内容的消息信封 */
    private MessageEnvelope<Object> envelope() {
        return new MessageEnvelope<>()
                .setMessageId("message-7")
                .setEvent("order.created")
                .setBusinessKey("order:7")
                .setTraceId("trace-7")
                .setSource("sca4d-server")
                .setPublishedAt(OffsetDateTime.parse("2026-07-11T14:00:00+08:00"))
                .setHeaders(Map.of("custom", "value"))
                .setPayload(Map.of("id", 7L));
    }
}
