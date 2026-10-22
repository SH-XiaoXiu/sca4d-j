package com.sca4d.iot.emqx.transport;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryDecision;
import com.sca4d.core.api.retry.RetryExecutor;
import com.sca4d.core.api.retry.RetryInterruptedException;
import com.sca4d.core.api.retry.RetryListener;
import com.sca4d.core.api.retry.RetryPolicy;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/** 在 MQTT 网络回调之外按 Topic 分片处理上行，并限制队列、处理次数和最终确认条件。 */
final class MqttInboundProcessor implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MqttInboundProcessor.class);

    private final @NotNull Consumer<MqttMessage> router;

    private final @NotNull List<MqttMessageProcessingObserver> processingObservers;

    private final @NotNull List<MqttMessageObserver> receptionObservers;

    private final @Nullable MqttInboundFailureStore failureStore;

    private final @NotNull List<ThreadPoolExecutor> workers;

    private final @NotNull RetryPolicy<Throwable> retryPolicy;

    private final @NotNull Duration shutdownTimeout;

    private final @NotNull RetryExecutor retries;

    private final int maximumAttempts;

    private final AtomicInteger pending = new AtomicInteger();

    private final AtomicInteger retrying = new AtomicInteger();

    private final LongAdder processed = new LongAdder();

    private final LongAdder rejected = new LongAdder();

    private final LongAdder quarantined = new LongAdder();

    private final LongAdder unacknowledged = new LongAdder();

    private final LongAdder overloaded = new LongAdder();

    private final LongAdder acknowledgementFailures = new LongAdder();

    private volatile boolean closed;

    /**
     * 创建具有固定分片数和有界等待队列的 MQTT 上行处理器。
     *
     * @param router 实际协议和业务路由入口
     * @param receptionObservers 原始消息观察器
     * @param processingObservers 处理结果观察器
     * @param failureStore 可选可靠失败存储；缺失时最终失败不会 ACK
     * @param workerCount 相同 Topic 固定进入同一项的分片数量
     * @param queueCapacity 每个分片允许等待的消息数量
     * @param maximumAttempts 未明确拒绝的消息最大处理次数
     * @param retryDelay 相邻处理尝试的等待时间
     * @param shutdownTimeout 停止时等待队列排空的最长时间
     * @param retries 同步重试执行器
     */
    public MqttInboundProcessor(
            @NotNull Consumer<MqttMessage> router,
            @NotNull Collection<MqttMessageObserver> receptionObservers,
            @NotNull Collection<MqttMessageProcessingObserver> processingObservers,
            @Nullable MqttInboundFailureStore failureStore,
            int workerCount,
            int queueCapacity,
            int maximumAttempts,
            @NotNull Duration retryDelay,
            @NotNull Duration shutdownTimeout,
            @NotNull RetryExecutor retries) {
        this.router = Objects.requireNonNull(router, "MQTT 上行路由入口不能为空");
        this.receptionObservers = List.copyOf(
                Objects.requireNonNull(receptionObservers, "MQTT 接收观察器不能为空"));
        this.processingObservers = List.copyOf(
                Objects.requireNonNull(processingObservers, "MQTT 处理观察器不能为空"));
        this.failureStore = failureStore;
        if (workerCount < 1 || queueCapacity < 1 || maximumAttempts < 1) {
            throw new IllegalArgumentException("MQTT 上行 worker、队列容量和最大处理次数必须为正数");
        }
        this.maximumAttempts = maximumAttempts;
        this.retryPolicy = RetryPolicy.forFailures(
                RetryRule.atMost(
                        maximumAttempts,
                        Backoff.fixed(positive(retryDelay, "MQTT 上行重试间隔"))),
                failure -> failure instanceof RuntimeException && !(failure instanceof MqttMessageRejectedException));
        this.retries = Objects.requireNonNull(retries, "MQTT 上行需要配置 RetryExecutor");
        this.shutdownTimeout = positive(shutdownTimeout, "MQTT 上行停止等待时间");
        List<ThreadPoolExecutor> executors = new ArrayList<>(workerCount);
        for (int index = 0; index < workerCount; index++) {
            executors.add(worker(index, queueCapacity));
        }
        this.workers = List.copyOf(executors);
    }

    /** 提交一条保持手动确认状态的 MQTT 上行；同一 Topic 始终进入同一个串行分片。 */
    public void submit(@NotNull Mqtt5Publish publish, @NotNull MqttMessage message) {
        Mqtt5Publish acknowledgement = Objects.requireNonNull(publish, "MQTT 待确认消息不能为空");
        MqttMessage input = Objects.requireNonNull(message, "MQTT 上行消息不能为空");
        if (closed) {
            overloaded.increment();
            LOG.warn("MQTT 上行处理器已停止，消息保持未确认 | topic={}", input.topic());
            return;
        }
        pending.incrementAndGet();
        try {
            workers.get(Math.floorMod(input.topic().hashCode(), workers.size()))
                    .execute(() -> process(acknowledgement, input));
        } catch (RejectedExecutionException exception) {
            pending.decrementAndGet();
            overloaded.increment();
            LOG.error(
                    "MQTT 上行分片队列已满，消息保持未确认 | topic={} payloadBytes={}",
                    input.topic(),
                    input.payloadSize());
        }
    }

    /** @return 当前处理数量和进程启动后的累计结果 */
    @CheckReturnValue
    public @NotNull MqttInboundStatus status() {
        return new MqttInboundStatus(
                pending.get(),
                retrying.get(),
                processed.sum(),
                rejected.sum(),
                quarantined.sum(),
                unacknowledged.sum(),
                overloaded.sum(),
                acknowledgementFailures.sum());
    }

    /** 停止接收新消息，在限定时间内排空队列，超时后中断等待再次处理的线程。 */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        workers.forEach(ThreadPoolExecutor::shutdown);
        long deadline = System.nanoTime() + nanos(shutdownTimeout);
        boolean interrupted = false;
        for (ThreadPoolExecutor worker : workers) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                if (!worker.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    break;
                }
            } catch (InterruptedException exception) {
                interrupted = true;
                break;
            }
        }
        int discarded = workers.stream()
                .filter(worker -> !worker.isTerminated())
                .mapToInt(worker -> worker.shutdownNow().size())
                .sum();
        if (discarded > 0 || pending.get() > 0) {
            LOG.warn(
                    "MQTT 上行停止等待超时，剩余消息保持未确认 | pending={} queued={}",
                    pending.get(),
                    discarded);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** 在同一 Topic 分片中完成接收观察、业务路由、再次处理和最终确认。 */
    private void process(Mqtt5Publish publish, MqttMessage message) {
        String deliveryId = UUID.randomUUID().toString();
        try {
            notifyReceived(message);
            AtomicInteger attempts = new AtomicInteger();
            try {
                retries.execute(retryPolicy, () -> {
                    attempts.incrementAndGet();
                    router.accept(message);
                    return null;
                }, new RetryListener() {
                    @Override
                    public void failed(@NotNull RetryContext<Throwable> context, @NotNull RetryDecision decision) {
                        if (decision.retrying()) {
                            notifyRetrying(
                                    deliveryId,
                                    message,
                                    Math.toIntExact(context.attempt()),
                                    (RuntimeException) context.failure());
                            LOG.warn(
                                    "MQTT 上行处理失败，等待再次处理 | topic={} payloadBytes={} attempt={} maximumAttempts={} failureType={}",
                                    message.topic(),
                                    message.payloadSize(),
                                    context.attempt(),
                                    maximumAttempts,
                                    context.failure().getClass().getSimpleName());
                        }
                    }

                    @Override
                    public void waiting(@NotNull RetryContext<Throwable> context, @NotNull Duration delay) {
                        retrying.incrementAndGet();
                    }

                    @Override
                    public void waited(@NotNull RetryContext<Throwable> context) {
                        retrying.decrementAndGet();
                    }
                });
                processed.increment();
                acknowledge(publish, message);
            } catch (MqttMessageRejectedException failure) {
                rejected.increment();
                notifyRejected(deliveryId, message, failure);
                LOG.warn("MQTT 上行报文已拒绝 | topic={} reason={}", message.topic(), failure.reason());
                acknowledge(publish, message);
            } catch (RetryInterruptedException failure) {
                LOG.debug("MQTT 上行等待已中断，消息保持未确认 | topic={}", message.topic());
            } catch (RuntimeException failure) {
                if (Thread.currentThread().isInterrupted())
                    return;
                storeFailure(publish, new MqttInboundFailure(deliveryId, message, attempts.get(), failure));
            }
        } finally {
            pending.decrementAndGet();
        }
    }

    /** 可靠保存最终失败；只有存储成功后才确认 Broker 消息。 */
    private void storeFailure(Mqtt5Publish publish, MqttInboundFailure failure) {
        if (failureStore == null) {
            unacknowledged.increment();
            notifyUnacknowledged(failure);
            LOG.error(
                    "MQTT 上行处理失败且未配置可靠失败存储，消息保持未确认 | topic={} attempts={} failureType={}",
                    failure.message().topic(),
                    failure.attempts(),
                    failure.failureType().getSimpleName());
            return;
        }
        try {
            failureStore.store(failure);
        } catch (RuntimeException storeFailure) {
            unacknowledged.increment();
            notifyUnacknowledged(failure);
            LOG.error(
                    "MQTT 上行最终失败无法可靠保存，消息保持未确认 | topic={} attempts={} storeFailureType={}",
                    failure.message().topic(),
                    failure.attempts(),
                    storeFailure.getClass().getSimpleName());
            return;
        }
        quarantined.increment();
        notifyQuarantined(failure);
        LOG.error(
                "MQTT 上行处理重试耗尽，消息已可靠保存 | topic={} payloadBytes={} attempts={} failureType={}",
                failure.message().topic(),
                failure.message().payloadSize(),
                failure.attempts(),
                failure.failureType().getSimpleName());
        acknowledge(publish, failure.message());
    }

    /** 只执行 MQTT 确认；确认失败不得再次调用业务路由。 */
    private void acknowledge(Mqtt5Publish publish, MqttMessage message) {
        try {
            publish.acknowledge();
        } catch (RuntimeException failure) {
            acknowledgementFailures.increment();
            LOG.error(
                    "MQTT 上行业务处理已结束但确认失败 | topic={} failureType={}",
                    message.topic(),
                    failure.getClass().getSimpleName());
        }
    }

    /** 通知原始接收观察器；观察失败不能改变消息确认结果。 */
    private void notifyReceived(MqttMessage message) {
        for (MqttMessageObserver observer : receptionObservers) {
            try {
                observer.messageReceived(message);
            } catch (RuntimeException exception) {
                LOG.warn(
                        "MQTT 入站消息观察失败，不影响业务路由 | observer={} topic={} failureType={}",
                        observer.getClass().getName(),
                        message.topic(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    /** 通知明确拒绝结果；观察器失败不能阻止释放 Broker 窗口。 */
    private void notifyRejected(
            String deliveryId,
            MqttMessage message,
            MqttMessageRejectedException failure) {
        for (MqttMessageProcessingObserver observer : processingObservers) {
            try {
                observer.messageRejected(deliveryId, message, failure);
            } catch (RuntimeException exception) {
                logObserverFailure(observer, message, exception);
            }
        }
    }

    /** 通知即将再次处理的失败；观察器失败不能改变重试决定。 */
    private void notifyRetrying(
            String deliveryId,
            MqttMessage message,
            int attempt,
            RuntimeException failure) {
        for (MqttMessageProcessingObserver observer : processingObservers) {
            try {
                observer.messageRetrying(deliveryId, message, attempt, maximumAttempts, failure);
            } catch (RuntimeException exception) {
                logObserverFailure(observer, message, exception);
            }
        }
    }

    /** 通知已经可靠保存的最终失败；观察器失败不能阻止 Broker 确认。 */
    private void notifyQuarantined(MqttInboundFailure failure) {
        for (MqttMessageProcessingObserver observer : processingObservers) {
            try {
                observer.messageQuarantined(failure);
            } catch (RuntimeException exception) {
                logObserverFailure(observer, failure.message(), exception);
            }
        }
    }

    /** 通知保持未确认的最终失败；观察器不能代替可靠失败存储。 */
    private void notifyUnacknowledged(MqttInboundFailure failure) {
        for (MqttMessageProcessingObserver observer : processingObservers) {
            try {
                observer.messageUnacknowledged(failure);
            } catch (RuntimeException exception) {
                logObserverFailure(observer, failure.message(), exception);
            }
        }
    }

    /** 记录处理观察器失败，避免同一异常在多个分支重复输出堆栈。 */
    private static void logObserverFailure(
            MqttMessageProcessingObserver observer,
            MqttMessage message,
            RuntimeException exception) {
        LOG.warn(
                "MQTT 上行处理结果观察失败，不影响确认决定 | observer={} topic={} failureType={}",
                observer.getClass().getName(),
                message.topic(),
                exception.getClass().getSimpleName());
    }

    /** 创建一个只有单个活动线程和固定等待容量的 Topic 分片。 */
    private static ThreadPoolExecutor worker(int index, int queueCapacity) {
        return new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform()
                        .daemon(true)
                        .name("sca4dj-mqtt-inbound-" + index + '-', 0)
                        .factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 校验重试和停止等待时间必须为正数。 */
    private static @NotNull Duration positive(@NotNull Duration value, @NotNull String name) {
        Duration checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return checked;
    }

    /** 把停止等待时间转换为不会溢出的纳秒数。 */
    private static long nanos(Duration value) {
        try {
            return value.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
