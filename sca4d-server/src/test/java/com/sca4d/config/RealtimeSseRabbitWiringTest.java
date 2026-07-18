package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.common.auth.Authentication;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.messaging.MessageCommand;
import com.sca4d.common.messaging.MessagePublishMode;
import com.sca4d.common.messaging.MessagePublisher;
import com.sca4d.common.messaging.rabbit.RabbitMessageSender;
import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.RealtimeTarget;
import com.sca4d.common.realtime.sse.SseMessagePublisher;
import com.sca4d.common.realtime.sse.SseRealtimeMessageListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 验证 SSE RabbitMQ 发布器、监听器和队列绑定。 */
class RealtimeSseRabbitWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class))
            .withUserConfiguration(JacksonConfig.class, MessagingConfig.class, RealtimeConfig.class)
            .withBean(Authentication.class, () -> mock(Authentication.class))
            .withBean(IdGenerator.class, () -> mock(IdGenerator.class))
            .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");

    @Test
    void shouldRegisterTypedSseRabbitListenerWithAnonymousQueue() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseRealtimeMessageListener.class);
            assertThat(context).hasSingleBean(Jackson2JsonMessageConverter.class);
            assertThat(context).hasSingleBean(RabbitMessageSender.class);
            assertThat(context).hasSingleBean(SseMessagePublisher.class);
            assertThat(context).hasSingleBean(RealtimePublisher.class);
            Jackson2JsonMessageConverter converter = context.getBean(Jackson2JsonMessageConverter.class);
            assertThat(context.getBean(RabbitTemplate.class).getMessageConverter()).isSameAs(converter);
            assertThat(context).doesNotHaveBean("sseRabbitMessageConverter");
            RabbitListenerEndpointRegistry registry = context.getBean(RabbitListenerEndpointRegistry.class);
            assertThat(registry.getListenerContainer("sseRealtimeMessageListener")).isNotNull();
            assertThat(context.getBeansOfType(Queue.class).values()).anySatisfy(queue -> {
                assertThat(queue.isDurable()).isFalse();
                assertThat(queue.isExclusive()).isTrue();
                assertThat(queue.isAutoDelete()).isTrue();
            });
        });
    }

    @Test
    void shouldPublishSseThroughRabbitAfterTransactionCommitWithoutWebsocket() {
        MessagePublisher messagePublisher = mock(MessagePublisher.class);

        contextRunner.withBean(MessagePublisher.class, () -> messagePublisher).run(context -> {
            assertThat(context).hasNotFailed();
            RealtimePublisher realtimePublisher = context.getBean(RealtimePublisher.class);

            TransactionSynchronizationManager.initSynchronization();
            try {
                realtimePublisher.publish(RealtimePublishRequest.sse(
                        RealtimeTarget.subject(41L), "notification.created", "payload"));
                verify(messagePublisher, Mockito.never()).publish(any());
                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCommit();
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }

            ArgumentCaptor<MessageCommand> command = ArgumentCaptor.forClass(MessageCommand.class);
            verify(messagePublisher).publish(command.capture());
            assertThat(command.getValue().getMode()).isEqualTo(MessagePublishMode.DIRECT);
            assertThat(command.getValue().getRoutingKey()).isEqualTo("subject.41");
        });
    }

    @Test
    void shouldNotRegisterSseRabbitListenerWhenSseIsDisabled() {
        contextRunner.withPropertyValues("sca4dj.realtime.sse.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SseRealtimeMessageListener.class);
            assertThat(context).hasBean("rabbitMessageConverter");
            RabbitListenerEndpointRegistry registry = context.getBean(RabbitListenerEndpointRegistry.class);
            assertThat(registry.getListenerContainer("sseRealtimeMessageListener")).isNull();
        });
    }

    @Test
    void shouldNotRegisterRabbitMessagingOrSseListenerWhenMessagingIsDisabled() {
        contextRunner.withPropertyValues("sca4dj.messaging.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("rabbitMessageConverter");
            assertThat(context).doesNotHaveBean(RabbitMessageSender.class);
            assertThat(context).doesNotHaveBean(SseRealtimeMessageListener.class);
            RabbitListenerEndpointRegistry registry = context.getBean(RabbitListenerEndpointRegistry.class);
            assertThat(registry.getListenerContainer("sseRealtimeMessageListener")).isNull();
        });
    }

    @Test
    void shouldUseCustomGlobalRabbitMessageConverter() {
        MessageConverter customConverter = mock(MessageConverter.class);

        contextRunner.withBean(MessageConverter.class, () -> customConverter).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("rabbitMessageConverter");
            assertThat(context.getBean(RabbitTemplate.class).getMessageConverter()).isSameAs(customConverter);
            assertThat(context).hasSingleBean(SseRealtimeMessageListener.class);
        });
    }
}
