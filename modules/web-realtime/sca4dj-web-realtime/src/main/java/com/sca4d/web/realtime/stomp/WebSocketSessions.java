package com.sca4d.web.realtime.stomp;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 记录 Spring 已建立的 WebSocket 句柄，使失效事件能在写出后定向关闭连接。 */
@ApiStatus.Internal
public final class WebSocketSessions implements WebSocketHandlerDecoratorFactory {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSessions.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 为 Spring 协议处理器增加连接句柄登记和清理。 */
    @Override
    public @NotNull WebSocketHandler decorate(@NotNull WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(
                    @NotNull WebSocketSession session,
                    @NotNull CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId(), session);
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    /** 在协议消息已经写出后以策略违规状态关闭指定 WebSocket。 */
    void close(@NotNull String simpSessionId) {
        WebSocketSession session = sessions.get(simpSessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException exception) {
            // Spring 的 SessionDisconnectEvent 仍会清理连接索引，日志只保留关闭失败上下文。
            LOG.debug(
                    "Web Realtime WebSocket 关闭失败 | simpSessionId={} errorType={}",
                    simpSessionId,
                    exception.getClass().getSimpleName());
        }
    }
}
