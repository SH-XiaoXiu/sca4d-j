package com.sca4d.common.realtime;

import org.jetbrains.annotations.NotNull;

/**
 * 实时通信基础路由。
 */
public final class RealtimeRoutes {

    /** 实时通信 HTTP 接口的共同路径前缀。 */
    public static final String HTTP_BASE = "/realtime";
    /** 查询当前实时订阅信息的相对路径。 */
    public static final String HTTP_SUBSCRIPTION = "/subscription";
    /** 建立 SSE 连接的相对路径。 */
    public static final String HTTP_SSE = "/sse";

    /** 建立原生 WebSocket 或 SockJS 连接的端点。 */
    public static final String WS_ENDPOINT = "/realtime/ws";

    /** 客户端发送 STOMP 应用消息时使用的目标前缀。 */
    public static final String APP_PREFIX = "/app";
    /** 广播 STOMP 消息的目标前缀。 */
    public static final String TOPIC_PREFIX = "/topic";
    /** 单用户 STOMP 消息的队列前缀。 */
    public static final String QUEUE_PREFIX = "/queue";
    /** Spring STOMP 用户目标前缀。 */
    public static final String USER_PREFIX = "/user";

    /** 根据实时 routing key 生成广播 topic 的前缀。 */
    public static final String REALTIME_TOPIC_PREFIX = TOPIC_PREFIX + "/realtime/";
    /** STOMP 帧处理错误发送到的用户队列。 */
    public static final String ERROR_QUEUE = QUEUE_PREFIX + "/errors";
    /** 已认证连接共同订阅的无敏感客户端状态变化主题。 */
    public static final String CLIENT_STATE_TOPIC = "client-state";

    /** 集群节点之间转发未在本节点找到的用户消息的 topic。 */
    public static final String USER_DESTINATION_BROADCAST = TOPIC_PREFIX + "/sca4dj.user-destination";
    /** 集群节点之间同步用户连接注册信息的 topic。 */
    public static final String USER_REGISTRY_BROADCAST = TOPIC_PREFIX + "/sca4dj.user-registry";

    /** 禁止实例化只保存固定实时通信路由的常量类型。 */
    private RealtimeRoutes() {
    }

    /**
     * 构造低层实时目标 STOMP topic。
     *
     * @param routingKey 实时目标 routing key
     * @return STOMP topic
     */
    public static @NotNull String realtimeTopic(@NotNull String routingKey) {
        return REALTIME_TOPIC_PREFIX + routingKey;
    }
}
