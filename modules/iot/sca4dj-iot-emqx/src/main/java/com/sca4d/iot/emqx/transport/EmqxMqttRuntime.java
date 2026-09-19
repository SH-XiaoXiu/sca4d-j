package com.sca4d.iot.emqx.transport;

import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.iot.emqx.config.EmqxProperties;
import com.sca4d.iot.emqx.connection.EmqxConnectionReconciler;
import com.sca4d.iot.emqx.protocol.EncodedMqttMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 维护单个应用实例的 MQTT 5 客户端、业务共享订阅、连接事件订阅和 QoS 1 发布。 */
public final class EmqxMqttRuntime implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(EmqxMqttRuntime.class);

    private static final List<String> CONNECTION_EVENT_FILTERS = List.of(
            "$SYS/brokers/+/clients/+/connected",
            "$SYS/brokers/+/clients/+/disconnected");

    private final @NotNull EmqxProperties properties;

    private final @NotNull MqttMessageRouter router;

    private final @NotNull Clock clock;

    private final @NotNull List<MqttMessageObserver> observers;

    private final @NotNull List<MqttMessageProcessingObserver> processingObservers;

    private final @Nullable MqttInboundFailureStore failureStore;

    private final @NotNull EmqxConnectionReconciler connectionReconciler;

    private final @NotNull Mqtt5AsyncClient client;

    private volatile @Nullable MqttInboundProcessor inbound;

    private volatile boolean running;

    /** 创建使用唯一 clientId 且自动重连后重新订阅的 MQTT 运行时。 */
    public EmqxMqttRuntime(
            @NotNull EmqxProperties properties,
            @NotNull MqttMessageRouter router,
            @NotNull Clock clock,
            @NotNull Collection<MqttMessageObserver> observers,
            @NotNull Collection<MqttMessageProcessingObserver> processingObservers,
            @Nullable MqttInboundFailureStore failureStore,
            @NotNull EmqxConnectionReconciler connectionReconciler) {
        this.properties = Objects.requireNonNull(properties, "EMQX 配置不能为空");
        this.properties.validate();
        this.router = Objects.requireNonNull(router, "MQTT 消息路由器不能为空");
        this.clock = Objects.requireNonNull(clock, "MQTT 时钟不能为空");
        this.observers = List.copyOf(Objects.requireNonNull(observers, "MQTT 消息观察器不能为空"));
        this.processingObservers = List.copyOf(
                Objects.requireNonNull(processingObservers, "MQTT 处理观察器不能为空"));
        this.failureStore = failureStore;
        this.connectionReconciler = Objects.requireNonNull(connectionReconciler, "EMQX 连接对账服务不能为空");
        String clientId = Objects.requireNonNull(properties.getClientId(), "EMQX clientId 不能为空");
        Mqtt5ClientBuilder builder = Mqtt5Client.builder()
                .identifier(clientId)
                .serverHost(properties.getHost())
                .serverPort(properties.getPort())
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(
                        context -> subscribe()
                                .thenRunAsync(this::reconcileAfterSubscribe)
                                .exceptionally(failure -> {
                                    LOG.error("EMQX MQTT 订阅未全部安装，不执行首次 Connection 对账", failure);
                                    return null;
                                }));
        if (properties.isTls())
            builder = builder.sslWithDefaultConfig();
        String username = properties.getUsername();
        if (username != null) {
            var simpleAuth = builder.simpleAuth().username(username);
            String password = properties.getPassword();
            if (password != null) {
                simpleAuth = simpleAuth.password(password.getBytes(StandardCharsets.UTF_8));
            }
            builder = simpleAuth.applySimpleAuth();
        }
        this.client = builder.buildAsync();
    }

    /** 连接 EMQX；Connected Listener 会安装业务和连接事件共享订阅。 */
    @Override
    public synchronized void start() {
        if (running)
            return;
        MqttInboundProcessor processor = new MqttInboundProcessor(
                router::dispatch,
                observers,
                processingObservers,
                failureStore,
                properties.getInboundWorkerCount(),
                properties.getInboundQueueCapacity(),
                properties.getInboundMaximumAttempts(),
                properties.getInboundRetryDelay(),
                properties.getOperationTimeout());
        inbound = processor;
        try {
            await(
                    client.connectWith()
                            .cleanStart(true)
                            .sessionExpiryInterval(0)
                            .restrictions()
                            .receiveMaximum(properties.inboundReceiveMaximum())
                            .applyRestrictions()
                            .send());
            running = true;
        } catch (RuntimeException exception) {
            inbound = null;
            processor.close();
            throw exception;
        }
        LOG.info(
                "EMQX MQTT 客户端已连接 | clientId={} host={} port={}",
                properties.getClientId(),
                properties.getHost(),
                properties.getPort());
    }

    /** 停止接收新消息并正常断开当前实例 MQTT 客户端。 */
    @Override
    public synchronized void stop() {
        if (!running)
            return;
        running = false;
        MqttInboundProcessor processor = inbound;
        try {
            if (processor != null) {
                processor.close();
            }
            if (client.getState().isConnectedOrReconnect())
                await(client.disconnect());
        } finally {
            inbound = null;
        }
        LOG.info("EMQX MQTT 客户端已断开 | clientId={}", properties.getClientId());
    }

    /** @return 当前 MQTT 客户端是否由 Spring 生命周期启动 */
    @Override
    public boolean isRunning() {
        return running;
    }

    /** @return 当前 MQTT 客户端是否实际处于已连接状态 */
    public boolean isConnected() {
        return client.getState() == MqttClientState.CONNECTED;
    }

    /** @return 当前进程 MQTT 上行处理状态；尚未启动或已经停止时返回空快照 */
    public @NotNull MqttInboundStatus inboundStatus() {
        MqttInboundProcessor processor = inbound;
        return processor == null ? MqttInboundStatus.stopped() : processor.status();
    }

    /** @return 在普通 HTTP listener 之后启动并在它之前停止 */
    @Override
    public int getPhase() {
        return 100;
    }

    /** 订阅完成且运行时仍由 Spring 启动时执行首次 Connection 对账。 */
    private synchronized void reconcileAfterSubscribe() {
        if (!running)
            return;
        try {
            if (connectionReconciler.runOnce()) {
                LOG.debug("EMQX MQTT 订阅完成后已更新 Connection 投影");
            }
        } catch (RuntimeException exception) {
            LOG.warn("EMQX MQTT 订阅完成后的 Connection 对账失败，等待周期重试", exception);
        }
    }

    /** 使用 QoS 1 和 Retain=false 发布，并等待 EMQX PUBACK。 */
    public void publish(@NotNull EncodedMqttMessage message) {
        if (client.getState() != MqttClientState.CONNECTED) {
            throw new IllegalStateException("EMQX MQTT 客户端未连接");
        }
        EncodedMqttMessage checked = Objects.requireNonNull(message, "MQTT 下行消息不能为空");
        Mqtt5Publish publish = Mqtt5Publish.builder()
                .topic(checked.topic())
                .payload(checked.payload())
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(false)
                .build();
        await(client.publish(publish));
    }

    /** 在首次连接和自动重连后恢复全部共享订阅，全部 SUBACK 成功后完成 Future。 */
    private @NotNull CompletableFuture<Void> subscribe() {
        String prefix = "$share/" + properties.getSharedSubscriptionGroup() + "/";
        List<CompletableFuture<Void>> subscriptions = new ArrayList<>();
        for (String filter : router.topicFilters()) {
            subscriptions.add(subscribe(prefix + filter, this::receive));
        }
        for (String filter : CONNECTION_EVENT_FILTERS) {
            subscriptions.add(subscribe(prefix + filter, this::receiveConnectionEvent));
        }
        return CompletableFuture.allOf(subscriptions.toArray(CompletableFuture[]::new));
    }

    /** 安装一个 QoS 1 共享订阅，并把拒绝的 SUBACK 转换为失败 Future。 */
    private @NotNull CompletableFuture<Void> subscribe(
            @NotNull String topicFilter,
            @NotNull Consumer<Mqtt5Publish> receiver) {
        Mqtt5Subscribe subscription = Mqtt5Subscribe.builder()
                .topicFilter(topicFilter)
                .qos(MqttQos.AT_LEAST_ONCE)
                .build();
        return client.subscribe(subscription, receiver, true).thenAccept(subAck -> {
            if (subAck.getReasonCodes().size() != 1 || subAck.getReasonCodes().getFirst().isError()) {
                throw new IllegalStateException(
                        "EMQX MQTT 共享订阅被拒绝: " + topicFilter + ", reason=" + subAck.getReasonCodes());
            }
            LOG.debug("EMQX MQTT 共享订阅已安装 | topicFilter={}", topicFilter);
        });
    }

    /** 把 MQTT 上行交给有界 Topic 分片，避免协议处理和下行发布阻塞网络回调。 */
    private void receive(Mqtt5Publish publish) {
        MqttInboundProcessor processor = inbound;
        if (processor == null) {
            LOG.warn("MQTT 上行处理器尚未运行，消息保持未确认 | topic={}", publish.getTopic());
            return;
        }
        processor.submit(
                publish,
                new MqttMessage(
                        publish.getTopic().toString(),
                        publish.getPayloadAsBytes(),
                        clock.instant(),
                        publish.getQos().getCode(),
                        publish.isRetain()));
    }

    /** 解析 EMQX `$SYS` Client 事件并用 Management API 当前状态刷新连接投影。 */
    private void receiveConnectionEvent(Mqtt5Publish publish) {
        try {
            String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
            String clientId = DataOps.j(payload).requiredText("clientid");
            connectionReconciler.refreshClient(clientId);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "EMQX Client 系统事件处理失败，仍将确认并等待后续事件或周期对账修复 | topic={}",
                    publish.getTopic(),
                    exception);
        }
        try {
            publish.acknowledge();
        } catch (RuntimeException exception) {
            LOG.error(
                    "EMQX Client 系统事件确认失败，等待 Broker 再次交付或周期对账修复 | topic={} failureType={}",
                    publish.getTopic(),
                    exception.getClass().getSimpleName());
        }
    }

    /** 使用应用配置的统一 MQTT 操作超时等待异步结果。 */
    private void await(java.util.concurrent.CompletableFuture<?> future) {
        try {
            future.orTimeout(properties.getOperationTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime)
                throw runtime;
            throw exception;
        }
    }
}
