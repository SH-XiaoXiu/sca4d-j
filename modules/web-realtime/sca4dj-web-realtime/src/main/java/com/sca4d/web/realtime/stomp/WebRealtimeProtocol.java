package com.sca4d.web.realtime.stomp;

import com.sca4d.core.api.setting.ConfiguredConstants;

import java.util.HashSet;
import java.util.Set;

/** 集中声明浏览器、具体应用和 Web Realtime 模块共同遵守的协议值。 */
public class WebRealtimeProtocol {

    /** Session 变化后要求浏览器重新读取当前 Session 的事件编码。 */
    public static final String AUTHENTICATION_SESSION_CHANGED_EVENT = "AUTHENTICATION/SESSION/CHANGED";

    /** Web Realtime 接受的唯一 STOMP 协议版本。 */
    public static final String STOMP_VERSION = "1.2";

    /** 原生 WebSocket 上的 Spring STOMP 入口。 */
    public static final String WEBSOCKET_ENDPOINT = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.websocket-endpoint",
            "/events/ws");

    /** 由具体应用 `@MessageMapping` 处理的 destination 前缀。 */
    public static final String APPLICATION_DESTINATION_PREFIX = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.application-destination-prefix",
            "/app");

    /** Spring 解析当前 WebSocket 连接 destination 使用的前缀。 */
    public static final String USER_DESTINATION_PREFIX = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.user-destination-prefix",
            "/user");

    /** Spring broker 处理点对点 destination 使用的前缀。 */
    public static final String QUEUE_DESTINATION_PREFIX = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.queue-destination-prefix",
            "/queue");

    /** Spring broker 处理广播 destination 使用的前缀。 */
    public static final String TOPIC_DESTINATION_PREFIX = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.topic-destination-prefix",
            "/topic");

    /** 浏览器订阅当前连接事件的用户 destination。 */
    public static final String USER_EVENT_DESTINATION = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.user-event-destination",
            USER_DESTINATION_PREFIX + QUEUE_DESTINATION_PREFIX + "/events");

    /** 服务端向当前 Spring STOMP Session 发送事件时使用的 broker destination。 */
    public static final String USER_EVENT_BROKER_DESTINATION = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.user-event-broker-destination",
            QUEUE_DESTINATION_PREFIX + "/events");

    /** `SEND` 和 `SUBSCRIBE` 选择当前业务主体使用的 STOMP native header。 */
    public static final String PRINCIPAL_ID_HEADER = ConfiguredConstants.text(
            "sca4dj.web-realtime.protocol.principal-id-header",
            "principal-id");

    private WebRealtimeProtocol() {
    }

    /** 校验公开 destination 不重叠，且用户事件订阅与 broker 目标一致。 */
    public static void validate() {
        Set<String> prefixes = new HashSet<>();
        for (String prefix : new String[] {
                APPLICATION_DESTINATION_PREFIX,
                USER_DESTINATION_PREFIX,
                QUEUE_DESTINATION_PREFIX,
                TOPIC_DESTINATION_PREFIX
        }) {
            path(prefix);
            if (!prefixes.add(prefix)) {
                throw new IllegalStateException("STOMP destination 前缀不能重复: " + prefix);
            }
        }
        path(WEBSOCKET_ENDPOINT);
        path(USER_EVENT_DESTINATION);
        path(USER_EVENT_BROKER_DESTINATION);
        if (!USER_EVENT_DESTINATION.startsWith(USER_DESTINATION_PREFIX + QUEUE_DESTINATION_PREFIX + "/")
                || !USER_EVENT_BROKER_DESTINATION.startsWith(QUEUE_DESTINATION_PREFIX + "/")
                || !PRINCIPAL_ID_HEADER.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
            throw new IllegalStateException("Web Realtime 用户事件 destination 或 Principal Header 配置无效");
        }
    }

    /** 校验 WebSocket 路径和 STOMP destination 使用无结尾斜杠的绝对路径。 */
    private static void path(String value) {
        if (!value.startsWith("/")
                || value.length() == 1
                || value.endsWith("/")
                || value.contains("//")
                || value.contains("?")
                || value.contains("#")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Web Realtime 路径格式无效: " + value);
        }
    }
}
