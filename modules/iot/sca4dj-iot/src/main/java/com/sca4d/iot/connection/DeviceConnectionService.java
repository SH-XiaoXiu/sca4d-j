package com.sca4d.iot.connection;

import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.connection.DeviceConnectionQueryService;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** 为 Device 当前连接可靠发布协议断连消息，并停止关联的通用连接。 */
public class DeviceConnectionService {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceConnectionService.class);

    private final @NotNull DeviceConnectionQueryService connections;

    private final @NotNull MessageBus messages;

    private final @NotNull Clock clock;

    private final @Nullable ConnectionRegistry commonConnections;

    /** 创建设备连接操作服务。 */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "ConnectionRegistry 是注入的共享协作接口，服务不拥有或修改其内部表示")
    public DeviceConnectionService(
            @NotNull DeviceConnectionQueryService connections,
            @NotNull MessageBus messages,
            @NotNull Clock clock,
            @Nullable ConnectionRegistry commonConnections) {
        this.connections = Objects.requireNonNull(connections, "Device Connection Repository 不能为空");
        this.messages = Objects.requireNonNull(messages, "MessageBus 不能为空");
        this.clock = Objects.requireNonNull(clock, "连接操作时钟不能为空");
        this.commonConnections = commonConnections;
    }

    /**
     * 为 Device 的每个当前连接登记可靠断连消息。
     *
     * <p>消息写入当前数据库事务；具体协议句柄由消息 Consumer 异步终止。存在通用
     * {@link ConnectionRegistry} 时，同步终止连接关联的 Session 投影，避免继续投递数据。
     *
     * @param deviceId Device 标识
     * @param reason 不包含凭据和隐私数据的稳定终止原因
     */
    @Transactional
    public void disconnectDevice(long deviceId, @NotNull String reason) {
        String checkedReason = Objects.requireNonNull(reason, "连接终止原因不能为空");
        List<DeviceConnection> current = connections.connections(deviceId);
        if (current.isEmpty()) {
            LOG.debug("Device 没有待断开的连接 | deviceId={} reason={}", deviceId, checkedReason);
            return;
        }

        // 每条消息绑定明确连接代次，协议端重复消费时不会断开后来建立的新连接。
        ConnectionRegistry registry = commonConnections;
        current.forEach(connection -> {
            DeviceDisconnectMessage payload = new DeviceDisconnectMessage(
                    deviceId,
                    connection.id(),
                    connection.protocol(),
                    checkedReason,
                    1);
            MessageId disconnectMessageId = messages.dispatch(
                    payload,
                    DispatchPolicy.outbox()).messageId();
            LOG.trace(
                    "Device 断连消息已登记 | deviceId={} connectionId={} disconnectMessageId={}",
                    deviceId,
                    connection.id().value(),
                    disconnectMessageId);
            if (registry != null) {
                connection.sessionId().ifPresent(sessionId -> registry.terminateBySession(sessionId, checkedReason));
            }
        });
        LOG.debug(
                "Device 断连消息已登记 | deviceId={} connectionCount={} reason={}",
                deviceId,
                current.size(),
                checkedReason);
    }
}
