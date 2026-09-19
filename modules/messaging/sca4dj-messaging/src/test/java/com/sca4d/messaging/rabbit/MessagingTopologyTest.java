package com.sca4d.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Queue;

import java.util.Collection;
import java.util.List;

/** 验证每个 Consumer 只有主队列和直接 DLQ，不声明自动重试队列。 */
class MessagingTopologyTest {

    @Test
    @DisplayName("Declares One Durable Queue And Direct DLQ Per Consumer")
    void declaresOneDurableQueueAndDirectDlqPerConsumer() {
        TestConsumer consumer = new TestConsumer("billing.invoice", "invoice.created");
        MessagingTopology topology = new MessagingTopology(new RuntimeNamespace("test-app"));

        Collection<Declarable> declarations = topology.declarables(List.of(consumer)).getDeclarables();
        List<Queue> queues = declarations.stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .toList();

        assertThat(queues).hasSize(2);
        Queue main = queues.stream()
                .filter(queue -> !queue.getName().endsWith(":dead-letter"))
                .findFirst()
                .orElseThrow();
        Queue deadLetter = queues.stream()
                .filter(queue -> queue.getName().endsWith(":dead-letter"))
                .findFirst()
                .orElseThrow();
        assertThat(main.isDurable()).isTrue();
        assertThat(main.getArguments())
                .containsEntry("x-dead-letter-exchange", "test-app:sca4dj:messaging:dead-letter")
                .containsEntry("x-dead-letter-routing-key", main.getName())
                .doesNotContainKeys("x-message-ttl");
        assertThat(deadLetter.isDurable()).isTrue();
        assertThat(deadLetter.getArguments()).doesNotContainKeys("x-dead-letter-exchange", "x-message-ttl");
        assertThat(declarations).noneMatch(
                item -> item instanceof Queue queue
                        && queue.getName().contains("retry"));
    }

    @Test
    @DisplayName("Rejects Duplicate Consumer Codes")
    void rejectsDuplicateConsumerCodes() {
        assertThatThrownBy(
                () -> new MessagingTopology(new RuntimeNamespace("test-app"))
                        .declarables(
                                List.of(
                                        new TestConsumer("duplicate", "invoice.created"),
                                        new TestConsumer("duplicate", "invoice.cancelled"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    /** 测试使用的稳定 Consumer 契约。 */
    private static final class TestConsumer implements MessageConsumer<String> {
        private final String code;

        private final String event;

        /** 创建测试 Consumer。 */
        private TestConsumer(String code, String event) {
            this.code = code;
            this.event = event;
        }

        @Override
        public @NotNull String consumerCode() {
            return code;
        }

        @Override
        public @NotNull EventType<String> eventType() {
            return new EventType<>(event, 1, String.class);
        }

        @Override
        public void consume(@NotNull MessageDelivery<String> delivery) {
        }
    }
}
