package com.sca4d.iot.emqx.management;

import com.sca4d.iot.emqx.connection.EmqxClientConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** 调用 EMQX `5.8.9` Management API 查询、终止连接并发布 MQTT 消息。 */
public interface EmqxManagementClient {

    /** @param clientId MQTT clientId；已离线时也视为成功 */
    void disconnect(@NotNull String clientId);

    /** @return EMQX 集群当前全部 MQTT Client Connection */
    @NotNull
    List<EmqxClientConnection> findClients();

    /**
     * @param clientId MQTT Client ID
     * @return 当前在线连接；EMQX 返回 404 时为空
     */
    @NotNull
    Optional<EmqxClientConnection> findClient(@NotNull String clientId);

    /**
     * 通过 EMQX Broker 发布一条 MQTT 消息。
     *
     * @param publication Topic、原始 payload、QoS 和 Retain
     */
    void publish(@NotNull MqttPublication publication);
}
