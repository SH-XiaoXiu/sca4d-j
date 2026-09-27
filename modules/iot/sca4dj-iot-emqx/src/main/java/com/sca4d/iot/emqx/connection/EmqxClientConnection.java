package com.sca4d.iot.emqx.connection;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 保存 EMQX Management API 返回的一条当前 MQTT Client Connection。 */
public final class EmqxClientConnection {

    private final @NotNull String clientId;

    private final @NotNull String brokerNode;

    private final @NotNull Instant connectedAt;

    private final @NotNull Map<String, String> clientAttributes;

    /** 创建用于连接对账的 Broker 当前连接信息。 */
    public EmqxClientConnection(
            @NotNull String clientId,
            @NotNull String brokerNode,
            @NotNull Instant connectedAt,
            @NotNull Map<String, String> clientAttributes) {
        this.clientId = TextUtils.requireText(clientId, "EMQX clientId");
        this.brokerNode = TextUtils.requireText(brokerNode, "EMQX node");
        this.connectedAt = Objects.requireNonNull(connectedAt, "EMQX connectedAt 不能为空");
        this.clientAttributes = Map.copyOf(Objects.requireNonNull(clientAttributes, "EMQX client attrs 不能为空"));
    }

    /** @return MQTT clientId */
    public @NotNull String clientId() {
        return clientId;
    }

    /** @return 当前 EMQX 节点 */
    public @NotNull String brokerNode() {
        return brokerNode;
    }

    /** @return Broker 报告的连接时间 */
    public @NotNull Instant connectedAt() {
        return connectedAt;
    }

    /** @return HTTP Authenticator 写入的非敏感属性 */
    public @NotNull Map<String, String> clientAttributes() {
        return clientAttributes;
    }

    /** @return 从 EMQX 当前连接快照生成且不暴露 Client ID 的连接代次标识 */
    public @NotNull DeviceConnectionId connectionId() {
        return EmqxConnectionIds.from(clientId, connectedAt);
    }
}
