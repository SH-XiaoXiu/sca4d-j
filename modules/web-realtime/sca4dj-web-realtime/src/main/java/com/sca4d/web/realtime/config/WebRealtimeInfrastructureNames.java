package com.sca4d.web.realtime.config;

import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 为 Web Realtime 的 Redis 键和 RabbitMQ 对象生成无歧义运行时名称。 */
@ApiStatus.Internal
public class WebRealtimeInfrastructureNames {

    /** 创建 Web Realtime 基础设施的逻辑名称目录。 */
    public WebRealtimeInfrastructureNames() {
    }

    /** @return Web Realtime 节点消息使用的非持久 direct exchange 名称 */
    @Contract(pure = true)
    @NotNull
    public Key nodeExchange() {
        return Key.of(TreeCode.ofColon("WEB_REALTIME:NODE:EXCHANGE"));
    }

    /** @return 指定节点的独占自动删除队列名称 */
    @Contract(pure = true)
    @NotNull
    public Key nodeQueue(@NotNull String nodeId) {
        return Key.of(TreeCode.ofColon("WEB_REALTIME:NODE"), nodeId).append(TreeCode.ofSegment("QUEUE"));
    }

    /** @return 指定连接元数据的 Redis Hash 键 */
    @Contract(pure = true)
    @NotNull
    public Key connection(@NotNull ConnectionId connectionId) {
        return Key.of(TreeCode.ofColon("WEB_REALTIME:CONNECTION")).part(connectionId.value());
    }

    /** @return 指定连接全部目标 Redis 键的 Set 键 */
    @Contract(pure = true)
    @NotNull
    public Key connectionTargets(@NotNull ConnectionId connectionId) {
        return connection(connectionId).append(TreeCode.ofSegment("TARGETS"));
    }

    /** @return 指定连接的 STOMP 订阅 ID 到目标 Redis 键映射 Hash */
    @Contract(pure = true)
    @NotNull
    public Key connectionSubscriptions(@NotNull ConnectionId connectionId) {
        return connection(connectionId).append(TreeCode.ofSegment("SUBSCRIPTIONS"));
    }

    /** @return 不暴露 Resource、Topic 或其他目标原文的目标 SET 键 */
    @Contract(pure = true)
    @NotNull
    public Key target(@NotNull WebRealtimeTarget target) {
        return Key.of(TreeCode.ofColon("WEB_REALTIME:TARGET")).part(target.key());
    }

    /** @return 一次性 WebSocket 票据的 Redis String 键 */
    @Contract(pure = true)
    @NotNull
    public Key ticket(@NotNull String ticket) {
        return Key.of(TreeCode.ofColon("WEB_REALTIME:TICKET")).part(ticket);
    }

}
