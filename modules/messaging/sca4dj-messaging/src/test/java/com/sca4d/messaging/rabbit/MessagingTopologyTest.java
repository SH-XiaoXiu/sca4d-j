package com.sca4d.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Queue;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/** 验证每个 Handler 获得独立主队列、主队列和 DLQ，延迟重试由 Outbox 保存。 */
class RabbitMessageTopologyTest {
    static {
        AppName.publish("sca4d-test");
    }

    @Test
    @DisplayName("无重试 Handler 只声明持久主队列和 DLQ")
    void declaresMainQueueAndDlqWithoutRetryQueue() {
        TestHandler handler = new TestHandler("BILLING/INVOICE", "INVOICE/CREATED", RetryRule.once());
        RabbitMessageTopology topology = new RabbitMessageTopology();

        Collection<Declarable> declarations = topology.declarables(List.of(handler), List.of()).getDeclarables();
        List<Queue> queues = declarations.stream().filter(Queue.class::isInstance).map(Queue.class::cast).toList();

        assertThat(queues).hasSize(2);
        Queue main = queues.stream()
                .filter(queue -> !queue.getName().endsWith(":DEAD_LETTER"))
                .findFirst()
                .orElseThrow();
        Queue deadLetter = queues.stream()
                .filter(queue -> queue.getName().endsWith(":DEAD_LETTER"))
                .findFirst()
                .orElseThrow();
        assertThat(main.isDurable()).isTrue();
        assertThat(main.getArguments())
                .containsEntry("x-dead-letter-exchange", "SCA4D_TEST:MESSAGING:DEAD_LETTER")
                .containsEntry("x-dead-letter-routing-key", main.getName())
                .doesNotContainKeys("x-message-ttl");
        assertThat(deadLetter.isDurable()).isTrue();
        assertThat(declarations).noneMatch(item -> item instanceof Queue queue && queue.getName().endsWith(":RETRY"));
    }

    @Test
    @DisplayName("重试 Handler 通过定向主队列投递，不建立延迟队列")
    void declaresRetryQueueForRetryingHandler() {
        TestHandler handler = new TestHandler(
                "BILLING/INVOICE",
                "INVOICE/CREATED",
                RetryRule.atMost(3, Backoff.fixed(Duration.ofSeconds(5))));
        Collection<Declarable> declarations = new RabbitMessageTopology()
                .declarables(List.of(handler), List.of())
                .getDeclarables();

        assertThat(declarations.stream().filter(Queue.class::isInstance).map(Queue.class::cast).toList())
                .hasSize(2)
                .allSatisfy(queue -> assertThat(queue.getArguments()).doesNotContainKey("x-message-ttl"));
        assertThat(new RabbitMessageTopology().handlerQueueName(handler.code()).format(NameFormat.COLON))
                .isEqualTo("SCA4D_TEST:MESSAGING:HANDLER:BILLING:INVOICE");
    }

    @Test
    @DisplayName("重复 Handler 编码被拒绝")
    void rejectsDuplicateHandlerCodes() {
        assertThatThrownBy(
                () -> new RabbitMessageTopology()
                        .declarables(
                                List.of(
                                        new TestHandler(
                                                "BILLING/DUPLICATE",
                                                "INVOICE/CREATED",
                                                RetryRule.once()),
                                        new TestHandler(
                                                "BILLING/DUPLICATE",
                                                "INVOICE/CANCELLED",
                                                RetryRule.once())),
                                List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    /** 测试用的 EventHandler 实现，声明固定的 EventType 与 RetryRule。 */
    private static class TestHandler implements EventHandler<String> {
        private final EventType<String> eventType;

        private final RetryRule policy;

        private final TreeCode code;

        private TestHandler(String code, String eventCode, RetryRule policy) {
            this.code = TreeCode.of(code);
            this.eventType = new EventType<>(eventCode, 1, String.class);
            this.policy = policy;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public @NotNull EventType<String> eventType() {
            return eventType;
        }

        @Override
        public @NotNull RetryRule retryRule() {
            return policy;
        }

        @Override
        public void handleEvent(@NotNull IncomingMessage<Event<String>> message) {
        }
    }
}
