package com.sca4d.web.realtime.stomp;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.web.realtime.connection.BrowserWrite;
import com.sca4d.web.realtime.connection.LocalConnection;
import com.sca4d.web.realtime.connection.LocalConnectionRegistry;
import com.sca4d.web.realtime.connection.NodeRouter;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 把 Spring STOMP Session 生命周期接入 ConnectionId、Redis 路由和本机连接句柄。 */
@ApiStatus.Internal
public class StompConnections {

    static final String CONNECTION_ATTRIBUTE = StompConnections.class.getName() + ".connection";

    static final String CLOSE_AFTER_HEADER = StompConnections.class.getName() + ".closeAfter";

    private static final Logger LOG = LoggerFactory.getLogger(StompConnections.class);

    private final @NotNull LocalConnectionRegistry local;

    private final @NotNull RedisConnectionRegistry shared;

    private final @NotNull NodeRouter transport;

    private final @NotNull SimpMessagingTemplate messaging;

    private final @NotNull WebSocketSessions sockets;

    private final Map<String, StompConnection> connections = new ConcurrentHashMap<>();

    /** 创建 Spring STOMP 生命周期与 Web Realtime 本机、共享连接索引之间的桥接。 */
    public StompConnections(
            @NotNull RedisConnectionRegistry shared,
            @NotNull LocalConnectionRegistry local,
            @NotNull NodeRouter transport,
            @NotNull SimpMessagingTemplate messaging,
            @NotNull WebSocketSessions sockets) {
        this.shared = Objects.requireNonNull(shared, "Web Realtime 共享索引不能为空");
        this.local = Objects.requireNonNull(local, "本机连接注册表不能为空");
        this.transport = Objects.requireNonNull(transport, "Web Realtime 节点传输不能为空");
        this.messaging = Objects.requireNonNull(messaging, "SimpMessagingTemplate 不能为空");
        this.sockets = Objects.requireNonNull(sockets, "WebSocket 连接句柄不能为空");
    }

    /** 在 Spring 已接受 STOMP CONNECT 后登记本机句柄和全部自动身份目标。 */
    @EventListener
    public void connected(@NotNull SessionConnectedEvent event) {
        // 只接受由一次性票据握手创建且包含可写 Session 属性的 Spring STOMP 连接。
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String simpSessionId = headers.getSessionId();
        // 延迟到达的 CONNECTED 不能让已断开的 WebSocket 重新登记本机和 Redis 连接。
        Map<String, Object> attributes = simpSessionId == null ? null : sockets.attributes(simpSessionId);
        Object sessionValue = attributes == null
                ? null
                : attributes.get(TicketHandshakeInterceptor.SESSION_ATTRIBUTE);
        if (simpSessionId == null
                || attributes == null
                || !(sessionValue instanceof Session session)) {
            LOG.warn("Spring STOMP 连接缺少票据握手身份，连接将关闭 | simpSessionId={}", simpSessionId);
            if (simpSessionId != null) {
                sockets.close(simpSessionId);
            }
            return;
        }
        ConnectionId connectionId = new ConnectionId(UUID.randomUUID().toString());
        StompConnection connection = new StompConnection(connectionId, simpSessionId);
        if (connections.putIfAbsent(simpSessionId, connection) != null) {
            sockets.close(simpSessionId);
            return;
        }
        try {
            // 先登记本机句柄，再写入 Redis 自动身份目标；写入成功后，后面的帧才能读取 ConnectionId。
            local.add(connection);
            shared.register(connectionId, transport.nodeId(), session);
            attributes.put(CONNECTION_ATTRIBUTE, connectionId);
            if (!sockets.accepts(simpSessionId)) {
                // 断连事件可能先于 Redis 登记完成，补偿删除刚写入的本机和共享索引。
                connections.remove(simpSessionId, connection);
                local.remove(connectionId);
                return;
            }
            LOG.debug(
                    "Spring STOMP 连接已登记 | connectionId={} sessionId={} accountId={}",
                    connectionId,
                    session.id(),
                    session.account().accountId());
        } catch (RuntimeException exception) {
            // 部分登记失败时立即移除本机句柄；Redis 残留索引由租约最终清理。
            connections.remove(simpSessionId, connection);
            try {
                local.remove(connectionId);
            } catch (RuntimeException ignored) {
                // 连接租约最终会清理已部分登记的共享索引。
            }
            LOG.warn(
                    "Spring STOMP 连接登记失败，连接将关闭 | simpSessionId={} errorType={}",
                    simpSessionId,
                    exception.getClass().getSimpleName());
            sockets.close(simpSessionId);
        }
    }

    /** 在 Spring 断开通知时幂等删除本机句柄和 Redis 共享索引。 */
    @EventListener
    public void disconnected(@NotNull SessionDisconnectEvent event) {
        StompConnection connection = connections.remove(event.getSessionId());
        if (connection == null) {
            return;
        }
        try {
            local.remove(connection.id());
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Spring STOMP 连接共享索引删除失败，将由租约过期清理 | connectionId={} errorType={}",
                    connection.id(),
                    exception.getClass().getSimpleName());
        }
    }

    /** 按 Spring STOMP Session 发送浏览器信封，写出完成后可请求关闭实际连接。 */
    private class StompConnection implements LocalConnection {

        private final @NotNull ConnectionId id;

        private final @NotNull String simpSessionId;

        /** 创建已被 Spring 确认的 STOMP 连接。 */
        private StompConnection(
                @NotNull ConnectionId id,
                @NotNull String simpSessionId) {
            this.id = id;
            this.simpSessionId = simpSessionId;
        }

        /** @return 部署范围内唯一的 Web Realtime ConnectionId */
        @Override
        public @NotNull ConnectionId id() {
            return id;
        }

        /** 用 Spring Session 定向 user destination 投递浏览器信封，不自行编码 STOMP MESSAGE 帧。 */
        @Override
        public void offer(@NotNull BrowserWrite delivery) {
            BrowserWrite checked = Objects.requireNonNull(delivery, "连接投递不能为空");
            SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId(simpSessionId);
            headers.setHeader(CLOSE_AFTER_HEADER, checked.closeAfter());
            if (checked.closeAfter()) {
                // Relay 只往返 STOMP native headers；写出前由出站拦截器移除此内部标记。
                headers.setNativeHeader(CLOSE_AFTER_HEADER, "true");
            }
            headers.setLeaveMutable(true);
            messaging.convertAndSendToUser(
                    simpSessionId,
                    WebRealtimeProtocol.USER_EVENT_BROKER_DESTINATION,
                    checked.envelope(),
                    headers.getMessageHeaders());
        }

        /** 请求 Spring WebSocket transport 关闭当前连接。 */
        @Override
        public void close() {
            sockets.close(simpSessionId);
        }
    }
}
