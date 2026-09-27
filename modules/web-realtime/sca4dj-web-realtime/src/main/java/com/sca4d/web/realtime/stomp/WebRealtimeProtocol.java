package com.sca4d.web.realtime.stomp;

/** 集中声明浏览器、具体应用和 Web Realtime 模块共同遵守的固定协议值。 */
public class WebRealtimeProtocol {

    /** 原生 WebSocket 上的 Spring STOMP 入口。 */
    public static final String WEBSOCKET_ENDPOINT = "/events/ws";

    /** 由具体应用 `@MessageMapping` 处理的 destination 前缀。 */
    public static final String APPLICATION_DESTINATION_PREFIX = "/app";

    /** Spring 解析当前 WebSocket 连接 destination 使用的前缀。 */
    public static final String USER_DESTINATION_PREFIX = "/user";

    /** Spring broker 处理点对点 destination 使用的前缀。 */
    public static final String QUEUE_DESTINATION_PREFIX = "/queue";

    /** Spring broker 处理广播 destination 使用的前缀。 */
    public static final String TOPIC_DESTINATION_PREFIX = "/topic";

    /** 浏览器订阅当前连接事件的用户 destination。 */
    public static final String USER_EVENT_DESTINATION = USER_DESTINATION_PREFIX + "/queue/events";

    /** 服务端向当前 Spring STOMP Session 发送事件时使用的 broker destination。 */
    public static final String USER_EVENT_BROKER_DESTINATION = QUEUE_DESTINATION_PREFIX + "/events";

    /** Web Realtime 接受的唯一 STOMP 协议版本。 */
    public static final String STOMP_VERSION = "1.2";

    /** `SEND` 和 `SUBSCRIBE` 选择当前业务主体使用的 STOMP native header。 */
    public static final String PRINCIPAL_ID_HEADER = "principal-id";

    /** Session 变化后要求浏览器重新读取当前 Session 的事件编码。 */
    public static final String AUTHENTICATION_SESSION_CHANGED_EVENT = "AUTHENTICATION/SESSION/CHANGED";

    private WebRealtimeProtocol() {
    }
}
