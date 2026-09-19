package com.sca4d.iot.connection;

import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import com.sca4d.iot.api.message.IotMessageTypes;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 消费可靠断连消息，并在协议实现暂时不可用时登记延迟重试。 */
public class DeviceDisconnectConsumer implements MessageConsumer<DeviceDisconnectMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceDisconnectConsumer.class);

    private static final int MAX_ATTEMPTS = 20;

    private final @NotNull DeviceConnectionTerminatorRouter terminators;

    private final @NotNull DeviceConnectionReceiver connections;

    private final @NotNull MessagePublisher messages;

    private final @NotNull Clock clock;

    private final @Nullable ConnectionRegistry commonConnections;

    /**
     * 创建使用通用 Messaging 重试、不保存 IoT 断连任务表的 Consumer。
     *
     * @param terminators 按协议选择真实连接终止实现的路由
     * @param connections 保存 Device 当前连接的投影入口
     * @param messages 发布延迟重试消息的通用入口
     * @param clock 计算下一次重试时间的时钟
     * @param commonConnections 可选公共应用连接投影；未安装时只清理 Device 投影
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "ConnectionRegistry 是注入的共享协作接口，Consumer 不拥有或修改其内部表示")
    public DeviceDisconnectConsumer(
            @NotNull DeviceConnectionTerminatorRouter terminators,
            @NotNull DeviceConnectionReceiver connections,
            @NotNull MessagePublisher messages,
            @NotNull Clock clock,
            @Nullable ConnectionRegistry commonConnections) {
        this.terminators = Objects.requireNonNull(terminators, "连接终止路由不能为空");
        this.connections = Objects.requireNonNull(connections, "Device 连接事件接收入口不能为空");
        this.messages = Objects.requireNonNull(messages, "MessagePublisher 不能为空");
        this.clock = Objects.requireNonNull(clock, "断连时钟不能为空");
        this.commonConnections = commonConnections;
    }

    @Override
    public @NotNull String consumerCode() {
        return "iot.device.disconnect";
    }

    @Override
    public @NotNull com.sca4d.core.api.event.EventType<DeviceDisconnectMessage> eventType() {
        return IotMessageTypes.DEVICE_DISCONNECT;
    }

    /**
     * 终止协议会话并删除协议、Device 和公共连接投影。
     *
     * <p>断连操作必须幂等。任一阶段暂时失败时发布下一次延迟消息；达到最大次数后重新抛出异常，由可靠消息基础设施进入 DLQ。
     *
     * @param delivery 包含协议、连接标识、原因和当前尝试次数的可靠消息
     */
    @Override
    @Transactional
    public void consume(@NotNull MessageDelivery<DeviceDisconnectMessage> delivery) {
        DeviceDisconnectMessage disconnect = Objects.requireNonNull(delivery, "断连消息投递不能为空")
                .event()
                .payload();
        try {
            // 先终止真实会话，再删除各层在线投影，避免会话仍存在时提前显示离线。
            terminators.disconnect(disconnect.protocol(), disconnect.connectionId(), disconnect.reason());
            boolean changed = connections.disconnected(disconnect.protocol(), disconnect.connectionId());
            if (commonConnections != null) {
                commonConnections.remove(new ConnectionId(disconnect.connectionId().value()));
            }
            LOG.debug(
                    "Device 连接已终止 | deviceId={} protocol={} connectionId={} projectionChanged={} attempt={}",
                    disconnect.deviceId(),
                    disconnect.protocol().code(),
                    disconnect.connectionId().value(),
                    changed,
                    disconnect.attempt());
        } catch (RuntimeException exception) {
            // 任一投影删除失败都重新执行协议终止和两类投影删除，直到成功或进入 DLQ。
            if (disconnect.attempt() >= MAX_ATTEMPTS) {
                throw exception;
            }
            int nextAttempt = disconnect.attempt() + 1;
            Instant retryAt = clock.instant().plus(backoff(disconnect.attempt()));
            DeviceDisconnectMessage retry = new DeviceDisconnectMessage(
                    disconnect.deviceId(),
                    disconnect.connectionId(),
                    disconnect.protocol(),
                    disconnect.reason(),
                    nextAttempt);
            MessageId retryMessageId = messages.publishReliably(
                    new Event<>(
                            IotMessageTypes.DEVICE_DISCONNECT,
                            "disconnect:" + disconnect.protocol().code() + ':'
                                    + disconnect.connectionId().value() + ':' + nextAttempt,
                            clock.instant(),
                            retry),
                    retryAt);
            LOG.warn(
                    "Device 断连等待重试 | deviceId={} protocol={} connectionId={} attempt={} retryAt={} retryMessageId={}",
                    disconnect.deviceId(),
                    disconnect.protocol().code(),
                    disconnect.connectionId().value(),
                    disconnect.attempt(),
                    retryAt,
                    retryMessageId);
        }
    }

    /** 返回最高五分钟的指数退避。 */
    private static @NotNull Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(300, 1L << Math.clamp(attempt - 1, 0, 9)));
    }
}
