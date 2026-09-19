package com.sca4d.iot.emqx.management;

import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionTerminator;
import com.sca4d.iot.emqx.connection.EmqxClientConnection;
import com.sca4d.iot.emqx.connection.EmqxConnectionRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** 根据通用 DeviceConnectionId 查找 EMQX Client ID，并通过 Management API 幂等终止连接。 */
public final class EmqxDeviceConnectionTerminator implements DeviceConnectionTerminator {

    private static final Logger LOG = LoggerFactory.getLogger(EmqxDeviceConnectionTerminator.class);

    private final @NotNull EmqxManagementClient client;

    private final @NotNull EmqxConnectionRepository connections;

    /**
     * @param client EMQX Management API 客户端
     * @param connections DeviceConnectionId 到 MQTT Client ID 的专用投影
     */
    public EmqxDeviceConnectionTerminator(
            @NotNull EmqxManagementClient client,
            @NotNull EmqxConnectionRepository connections) {
        this.client = Objects.requireNonNull(client, "EMQX ManagementClient 不能为空");
        this.connections = Objects.requireNonNull(connections, "EMQX Connection Repository 不能为空");
    }

    /** @return MQTT；MQTT over TCP 和 MQTT over WebSocket 使用同一个值 */
    @Override
    public @NotNull DeviceConnectionProtocol protocol() {
        return DeviceConnectionProtocol.MQTT;
    }

    /** 投影缺失时查询一次 Broker 当前连接；目标已经离线时仍视为成功。 */
    @Override
    public void disconnect(@NotNull DeviceConnectionId connectionId, @NotNull String reason) {
        DeviceConnectionId checkedId = Objects.requireNonNull(connectionId, "DeviceConnectionId 不能为空");
        Objects.requireNonNull(reason, "断连原因不能为空");
        String clientId = connections.findClientId(checkedId)
                .orElseGet(
                        () -> client.findClients()
                                .stream()
                                .filter(value -> checkedId.equals(value.connectionId()))
                                .map(EmqxClientConnection::clientId)
                                .findFirst()
                                .orElse(null));
        if (clientId == null) {
            LOG.debug("EMQX Connection 已经不存在 | connectionId={} reason={}", checkedId.value(), reason);
            return;
        }
        client.disconnect(clientId);
        connections.disconnected(checkedId);
        LOG.info("EMQX Connection 已终止 | connectionId={} reason={}", checkedId.value(), reason);
    }
}
