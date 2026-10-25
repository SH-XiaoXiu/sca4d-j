package com.sca4d.iot.connection;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;

/** 消费可靠断连消息，并在协议实现暂时不可用时登记延迟重试。 */
public class DeviceDisconnectHandler implements MessageHandler<DeviceDisconnectMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceDisconnectHandler.class);

    private static final int MAX_ATTEMPTS = 20;

    private final @Nullable ConnectionRegistry commonConnections;

    private final @NotNull DeviceConnectionReceiver connections;

    private final @NotNull DeviceConnectionTerminatorRouter terminators;

    private final @NotNull MessageCodec<DeviceDisconnectMessage> codec;

    /**
     * 创建使用通用 Messaging 重试、不保存 IoT 断连任务表的 Consumer。
     *
     * @param terminators 按协议选择真实连接终止实现的路由
     * @param connections 保存 Device 当前连接的投影入口
     * @param commonConnections 可选公共应用连接投影；未安装时只清理 Device 投影
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "ConnectionRegistry 是注入的共享协作接口，Consumer 不拥有或修改其内部表示")
    public DeviceDisconnectHandler(
            @NotNull DeviceConnectionTerminatorRouter terminators,
            @NotNull DeviceConnectionReceiver connections,
            @Nullable ConnectionRegistry commonConnections,
            @NotNull MessageCodec<DeviceDisconnectMessage> codec) {
        this.terminators = Objects.requireNonNull(terminators, "连接终止路由不能为空");
        this.connections = Objects.requireNonNull(connections, "Device 连接事件接收入口不能为空");
        this.commonConnections = commonConnections;
        this.codec = Objects.requireNonNull(codec, "断连消息 Codec 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("IOT/DEVICE/DISCONNECT/HANDLER");
    }

    @Override
    public @NotNull MessageCodec<DeviceDisconnectMessage> codec() {
        return codec;
    }

    @Override
    public @NotNull RetryRule retryRule() {
        return RetryRule.atMost(MAX_ATTEMPTS, Backoff.exponential(Duration.ofSeconds(1), 2, Duration.ofMinutes(5)));
    }

    /**
     * 终止协议会话并删除协议、Device 和公共连接投影。
     *
     * <p>断连操作必须幂等。任一阶段暂时失败时返回消费重试决定，由 Messaging 登记下一次定向投递或进入 DLQ。
     *
     * @param delivery 包含协议、连接标识、原因和当前尝试次数的可靠消息
     */
    @Override
    @Transactional
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<DeviceDisconnectMessage> delivery) {
        DeviceDisconnectMessage disconnect = Objects.requireNonNull(delivery, "断连消息投递不能为空")
                .message();
        String stage = "protocolTerminate";
        try {
            // 先终止真实会话，再删除各层在线投影，避免会话仍存在时提前显示离线。
            terminators.disconnect(disconnect.protocol(), disconnect.connectionId(), disconnect.reason());
            stage = "deviceProjection";
            boolean changed = connections.disconnected(disconnect.protocol(), disconnect.connectionId());
            if (commonConnections != null) {
                stage = "commonProjection";
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
            LOG.warn(
                    "Device 断连处理失败，等待重试 | deviceId={} protocol={} connectionId={} stage={} attempt={} errorType={}",
                    disconnect.deviceId(),
                    disconnect.protocol().code(),
                    disconnect.connectionId().value(),
                    stage,
                    disconnect.attempt(),
                    exception.getClass().getSimpleName());
            return HandlingDecision.retry(
                    exception.getClass().getSimpleName(),
                    Math.max(disconnect.attempt(), delivery.attempt()));
        }
        return HandlingDecision.acknowledge();
    }
}
