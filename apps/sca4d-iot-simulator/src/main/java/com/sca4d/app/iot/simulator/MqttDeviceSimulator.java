package com.sca4d.app.iot.simulator;

import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 维护一台模拟设备自己的 MQTT 5 连接、下行订阅和周期状态上报。 */
public abstract class MqttDeviceSimulator implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MqttDeviceSimulator.class);

    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(10);

    private final @NotNull String clientId;

    private final @NotNull Duration reportInterval;

    private final @NotNull Mqtt5AsyncClient client;

    private final @NotNull ScheduledExecutorService scheduler;

    private volatile boolean started;

    /**
     * 创建一台使用 MQTT Shared Secret 认证、无持久 Broker Session 的模拟设备。
     *
     * @param configuration 公共 Broker 和上报间隔配置
     * @param clientId Device 登记的 MQTT clientId
     * @param secret Device 创建或重置时显示一次的共享 Secret
     */
    protected MqttDeviceSimulator(
            @NotNull SimulatorConfiguration configuration,
            @NotNull String clientId,
            @NotNull String secret) {
        SimulatorConfiguration config = Objects.requireNonNull(configuration, "模拟器配置不能为空");
        this.clientId = requireText(clientId, "MQTT clientId");
        this.reportInterval = config.reportInterval();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "simulator-" + this.clientId);
            thread.setDaemon(false);
            return thread;
        });
        Mqtt5ClientBuilder builder = Mqtt5Client.builder()
                .identifier(this.clientId)
                .serverHost(config.host())
                .serverPort(config.port())
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(context -> {
                    if (started)
                        subscribe();
                });
        if (config.tls())
            builder.sslWithDefaultConfig();
        builder.simpleAuth()
                .username("mqtt-shared-secret")
                .password(requireText(secret, "Device Secret").getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth();
        this.client = builder.buildAsync();
    }

    /** 连接 EMQX、订阅设备自己的下行 Topic，并开始周期上报。 */
    public final synchronized void start() {
        if (started)
            return;
        started = true;
        try {
            await(client.connectWith().cleanStart(true).sessionExpiryInterval(0).send());
            scheduler.scheduleWithFixedDelay(
                    this::publishReportedSafely,
                    0,
                    reportInterval.toMillis(),
                    TimeUnit.MILLISECONDS);
            LOG.info("模拟设备已连接 | clientId={} downlinkTopic={}", clientId, downlinkTopic());
        } catch (RuntimeException exception) {
            started = false;
            throw exception;
        }
    }

    /** 停止周期上报并断开当前模拟设备连接。 */
    @Override
    public final synchronized void close() {
        if (!started)
            return;
        started = false;
        scheduler.shutdownNow();
        if (client.getState().isConnectedOrReconnect()) {
            await(client.disconnect());
        }
        LOG.info("模拟设备已断开 | clientId={}", clientId);
    }

    /** @return Device 登记的 MQTT clientId */
    protected final @NotNull String clientId() {
        return clientId;
    }

    /** 使用 QoS 1、Retain=false 发布一条真实设备上行消息并等待 PUBACK。 */
    protected final void publish(@NotNull String topic, byte @NotNull [] payload) {
        if (client.getState() != MqttClientState.CONNECTED) {
            throw new IllegalStateException("模拟设备 MQTT 尚未连接: " + clientId);
        }
        await(
                client.publishWith()
                        .topic(Objects.requireNonNull(topic, "上行 Topic 不能为空"))
                        .payload(Objects.requireNonNull(payload, "上行 payload 不能为空"))
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .retain(false)
                        .send());
    }

    /** @return 设备固件订阅的精确下行 Topic */
    protected abstract @NotNull String downlinkTopic();

    /** 生成并发布当前设备状态；周期任务和指令执行完成后都会调用。 */
    protected abstract void publishReported();

    /** 解析并执行一条设备下行 payload，再由具体固件发布业务 ACK 和新状态。 */
    protected abstract void receiveDownlink(byte @NotNull [] payload);

    /** 每次连接和自动重连后恢复无持久 Session 的精确下行订阅。 */
    private void subscribe() {
        client.subscribeWith()
                .topicFilter(downlinkTopic())
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(this::receive)
                .send()
                .whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        LOG.info("模拟设备下行订阅已安装 | clientId={} topic={}", clientId, downlinkTopic());
                    } else {
                        LOG.error(
                                "模拟设备下行订阅失败 | clientId={} topic={}",
                                clientId,
                                downlinkTopic(),
                                failure);
                    }
                });
    }

    /** 在 MQTT callback 中执行具体固件逻辑，失败时保留连接等待下一条指令。 */
    private void receive(@NotNull Mqtt5Publish publish) {
        try {
            receiveDownlink(publish.getPayloadAsBytes());
        } catch (RuntimeException exception) {
            LOG.warn(
                    "模拟设备拒绝下行报文 | clientId={} topic={}",
                    clientId,
                    publish.getTopic(),
                    exception);
        }
    }

    /** 隔离周期任务异常，单次构包或发布失败不会取消下一次上报。 */
    private void publishReportedSafely() {
        try {
            publishReported();
        } catch (RuntimeException exception) {
            LOG.warn("模拟设备周期上报失败 | clientId={}", clientId, exception);
        }
    }

    /** 使用固定超时等待 MQTT 异步操作。 */
    private static void await(@NotNull CompletableFuture<?> future) {
        try {
            future.orTimeout(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException)
                throw runtimeException;
            throw exception;
        }
    }

    /** 要求连接标识和认证材料为非空文本。 */
    private static @NotNull String requireText(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").trim();
        if (checked.isEmpty())
            throw new IllegalArgumentException(name + "不能为空");
        return checked;
    }
}
