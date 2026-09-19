package com.sca4d.web.realtime.config;

import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** 为 Web Realtime 的 Redis 键和 RabbitMQ 对象生成无歧义运行时名称。 */
@ApiStatus.Internal
public final class WebRealtimeInfrastructureNames {

    private final @NotNull RuntimeNamespace namespace;

    private final @NotNull String redisSlot;

    /** 创建仅使用当前应用 `RuntimeNamespace` 的基础设施命名对象。 */
    public WebRealtimeInfrastructureNames(@NotNull RuntimeNamespace namespace) {
        this.namespace = Objects.requireNonNull(namespace, "运行时命名空间不能为空");
        this.redisSlot = "{" + encoded(namespace.applicationId()) + "-web-realtime}";
    }

    /** @return Web Realtime 节点消息使用的非持久 direct exchange 名称 */
    @Contract(pure = true)
    @NotNull
    public String nodeExchange() {
        return namespace.sca4dj("web-realtime", "node", "exchange");
    }

    /** @return 指定节点的独占自动删除队列名称 */
    @Contract(pure = true)
    @NotNull
    public String nodeQueue(@NotNull String nodeId) {
        return namespace.sca4dj("web-realtime", "node", encoded(nodeId), "queue");
    }

    /** @return 指定连接元数据的 Redis Hash 键 */
    @Contract(pure = true)
    @NotNull
    public String connection(@NotNull ConnectionId connectionId) {
        return redis("connection", encoded(connectionId.value()));
    }

    /** @return 指定连接全部目标 Redis 键的 Set 键 */
    @Contract(pure = true)
    @NotNull
    public String connectionTargets(@NotNull ConnectionId connectionId) {
        return redis("connection", encoded(connectionId.value()), "targets");
    }

    /** @return 指定连接的 STOMP 订阅 ID 到目标 Redis 键映射 Hash */
    @Contract(pure = true)
    @NotNull
    public String connectionSubscriptions(@NotNull ConnectionId connectionId) {
        return redis("connection", encoded(connectionId.value()), "subscriptions");
    }

    /** @return 不暴露 Resource、Topic 或其他目标原文的目标 ZSET 键 */
    @Contract(pure = true)
    @NotNull
    public String target(@NotNull WebRealtimeTarget target) {
        return redis("target", encoded(target.key()));
    }

    /** @return 一次性 WebSocket 票据的 Redis String 键 */
    @Contract(pure = true)
    @NotNull
    public String ticket(@NotNull String ticket) {
        return redis("ticket", encoded(ticket));
    }

    /** @return 使用应用命名空间和共同 Cluster hash tag 的 Web Realtime Redis 键 */
    @Contract(pure = true)
    private @NotNull String redis(String @NotNull... segments) {
        String[] suffix = Objects.requireNonNull(segments, "Web Realtime Redis 名称段不能为空");
        String[] names = new String[suffix.length + 2];
        names[0] = "web-realtime";
        names[1] = redisSlot;
        System.arraycopy(suffix, 0, names, 2, suffix.length);
        return namespace.sca4dj(names);
    }

    /** @return UTF-8 文本的无填充 Base64URL 编码 */
    @Contract(pure = true)
    private static @NotNull String encoded(@NotNull String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        Objects.requireNonNull(value, "命名值不能为空").getBytes(StandardCharsets.UTF_8));
    }
}
