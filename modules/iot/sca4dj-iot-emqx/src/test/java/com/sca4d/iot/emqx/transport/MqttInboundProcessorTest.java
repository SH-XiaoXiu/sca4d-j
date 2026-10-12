package com.sca4d.iot.emqx.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.sca4d.core.spring.retry.SpringRetryExecutor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 验证 MQTT 上行的明确拒绝、有界队列、再次处理以及可靠保存后的确认条件。 */
class MqttInboundProcessorTest {

    private static final MqttMessage MESSAGE = message("vendor/gateways/gateway-1/info", 1);

    @Test
    void acknowledgesPermanentRejectionWithoutRetrying() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        MqttMessageProcessingObserver observer = mock(MqttMessageProcessingObserver.class);
        AtomicInteger attempts = new AtomicInteger();
        Consumer<MqttMessage> router = ignored -> {
            attempts.incrementAndGet();
            IllegalArgumentException cause = new IllegalArgumentException("invalid frame");
            throw new MqttMessageRejectedException(
                    MqttMessageRejectionReason.MESSAGE_INVALID,
                    "MQTT Payload 不符合协议消息定义",
                    cause);
        };

        try (MqttInboundProcessor processor = processor(router, List.of(), List.of(observer), null, 1, 4)) {
            processor.submit(publish, MESSAGE);

            verify(publish, timeout(1_000)).acknowledge();
            verify(observer, timeout(1_000)).messageRejected(any(), any(), any());
            assertThat(attempts).hasValue(1);
            await(() -> processor.status().pending() == 0);
            assertThat(processor.status().rejected()).isEqualTo(1);
        }
    }

    @Test
    void retriesTwiceAndAcknowledgesSuccessfulThirdAttempt() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        AtomicInteger attempts = new AtomicInteger();
        Consumer<MqttMessage> router = ignored -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
        };

        try (MqttInboundProcessor processor = processor(router, List.of(), List.of(), null, 1, 4)) {
            processor.submit(publish, MESSAGE);

            verify(publish, timeout(1_000)).acknowledge();
            await(() -> processor.status().pending() == 0);
            assertThat(attempts).hasValue(3);
            assertThat(processor.status().processed()).isEqualTo(1);
        }
    }

    @Test
    void acknowledgesFinalFailureOnlyAfterReliableStorage() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        MqttInboundFailureStore store = mock(MqttInboundFailureStore.class);
        MqttMessageProcessingObserver observer = mock(MqttMessageProcessingObserver.class);

        try (MqttInboundProcessor processor = processor(
                ignored -> {
                    throw new IllegalStateException("temporary");
                },
                List.of(),
                List.of(observer),
                store,
                1,
                4)) {
            processor.submit(publish, MESSAGE);

            verify(publish, timeout(1_000)).acknowledge();
            ArgumentCaptor<MqttInboundFailure> failure = ArgumentCaptor.forClass(MqttInboundFailure.class);
            verify(store, timeout(1_000)).store(failure.capture());
            verify(observer, timeout(1_000)).messageQuarantined(any());
            assertThat(failure.getValue().attempts()).isEqualTo(3);
            await(() -> processor.status().pending() == 0);
            assertThat(processor.status().quarantined()).isEqualTo(1);
        }
    }

    @Test
    void leavesFinalFailureUnacknowledgedWhenStorageIsMissingOrFails() {
        Mqtt5Publish missingStorePublish = mock(Mqtt5Publish.class);
        try (MqttInboundProcessor processor = processor(
                ignored -> {
                    throw new IllegalStateException("temporary");
                },
                List.of(),
                List.of(),
                null,
                1,
                4)) {
            processor.submit(missingStorePublish, MESSAGE);
            await(() -> processor.status().unacknowledged() == 1);
            verify(missingStorePublish, never()).acknowledge();
        }

        Mqtt5Publish failedStorePublish = mock(Mqtt5Publish.class);
        MqttInboundFailureStore failedStore = mock(MqttInboundFailureStore.class);
        doThrow(new IllegalStateException("store unavailable")).when(failedStore).store(any());
        try (MqttInboundProcessor processor = processor(
                ignored -> {
                    throw new IllegalStateException("temporary");
                },
                List.of(),
                List.of(),
                failedStore,
                1,
                4)) {
            processor.submit(failedStorePublish, MESSAGE);
            await(() -> processor.status().unacknowledged() == 1);
            verify(failedStorePublish, never()).acknowledge();
        }
    }

    @Test
    void acknowledgementFailureDoesNotExecuteBusinessRouteAgain() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        doThrow(new IllegalStateException("connection closed")).when(publish).acknowledge();
        AtomicInteger attempts = new AtomicInteger();

        try (MqttInboundProcessor processor = processor(
                ignored -> attempts.incrementAndGet(),
                List.of(),
                List.of(),
                null,
                1,
                4)) {
            processor.submit(publish, MESSAGE);
            await(() -> processor.status().acknowledgementFailures() == 1);

            assertThat(attempts).hasValue(1);
            assertThat(processor.status().processed()).isEqualTo(1);
        }
    }

    @Test
    void boundsEachShardQueueAndKeepsAnotherShardRunning() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Mqtt5Publish first = mock(Mqtt5Publish.class);
        Mqtt5Publish queued = mock(Mqtt5Publish.class);
        Mqtt5Publish overloaded = mock(Mqtt5Publish.class);
        Mqtt5Publish otherShard = mock(Mqtt5Publish.class);
        Consumer<MqttMessage> router = message -> {
            if ("a".equals(message.topic()) && firstStarted.getCount() > 0) {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        try (MqttInboundProcessor processor = processor(router, List.of(), List.of(), null, 2, 1)) {
            processor.submit(first, message("a", 1));
            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
            processor.submit(queued, message("a", 2));
            processor.submit(overloaded, message("a", 3));
            processor.submit(otherShard, message("b", 4));

            verify(otherShard, timeout(1_000)).acknowledge();
            await(() -> processor.status().overloaded() == 1);
            verify(overloaded, never()).acknowledge();
            releaseFirst.countDown();
            verify(first, timeout(1_000)).acknowledge();
            verify(queued, timeout(1_000)).acknowledge();
        } finally {
            releaseFirst.countDown();
        }
    }

    /** 关闭过程必须中断长退避，保留 Broker 未确认状态，不隔离为业务耗尽。 */
    @Test
    void shutdownInterruptsRetryWithoutAcknowledgingOrQuarantining() throws Exception {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        MqttInboundFailureStore store = mock(MqttInboundFailureStore.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch retrying = new CountDownLatch(1);
        MqttMessageProcessingObserver observer = new MqttMessageProcessingObserver() {
            @Override
            public void messageRetrying(
                    @NotNull String deliveryId,
                    @NotNull MqttMessage message,
                    int attempt,
                    int maximumAttempts,
                    @NotNull RuntimeException failure) {
                retrying.countDown();
            }
        };
        try (MqttInboundProcessor processor = new MqttInboundProcessor(message -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("temporary");
        },
                List.of(),
                List.of(observer),
                store,
                1,
                4,
                3,
                Duration.ofHours(1),
                Duration.ofMillis(1),
                new SpringRetryExecutor())) {
            processor.submit(publish, MESSAGE);
            assertThat(retrying.await(2, TimeUnit.SECONDS)).isTrue();
            processor.close();
            await(() -> processor.status().pending() == 0);
            assertThat(processor.status().retrying()).isZero();
        }
        assertThat(attempts).hasValue(1);
        verify(publish, never()).acknowledge();
        verify(store, never()).store(any());
    }

    /** 创建使用毫秒级等待和停止时间的测试处理器。 */
    private static MqttInboundProcessor processor(
            Consumer<MqttMessage> router,
            List<MqttMessageObserver> receptionObservers,
            List<MqttMessageProcessingObserver> processingObservers,
            MqttInboundFailureStore failureStore,
            int workerCount,
            int queueCapacity) {
        return new MqttInboundProcessor(
                router,
                receptionObservers,
                processingObservers,
                failureStore,
                workerCount,
                queueCapacity,
                3,
                Duration.ofMillis(1),
                Duration.ofSeconds(1),
                new SpringRetryExecutor());
    }

    /** 创建只靠 Topic 和单字节载荷区分的测试消息。 */
    private static MqttMessage message(String topic, int payload) {
        return new MqttMessage(
                topic,
                new byte[] {(byte) payload},
                Instant.parse("2026-09-08T01:00:00Z"),
                1,
                false);
    }

    /** 等待异步断言条件成立，超时后由最终断言报告实际状态。 */
    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
