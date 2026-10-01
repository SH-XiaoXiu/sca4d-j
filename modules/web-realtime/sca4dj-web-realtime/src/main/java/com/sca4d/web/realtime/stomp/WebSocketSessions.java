package com.sca4d.web.realtime.stomp;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** 记录 Spring 已建立的 WebSocket 句柄，使失效事件能在写出后定向关闭连接。 */
@ApiStatus.Internal
public class WebSocketSessions implements WebSocketHandlerDecoratorFactory {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSessions.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private static final String CLOSED_ATTRIBUTE = WebSocketSessions.class.getName() + ".closed";

    private final CopyOnWriteArrayList<Consumer<String>> closeListeners = new CopyOnWriteArrayList<>();

    /** 为 Spring 协议处理器增加连接句柄登记和清理。 */
    @Override
    public @NotNull WebSocketHandler decorate(@NotNull WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    sessions.remove(session.getId(), session);
                    closeListeners.forEach(listener -> listener.accept(session.getId()));
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(
                    @NotNull WebSocketSession session,
                    @NotNull CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId(), session);
                closeListeners.forEach(listener -> listener.accept(session.getId()));
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    /** 在协议消息已经写出后以策略违规状态关闭指定 WebSocket。 */
    void close(@NotNull String simpSessionId) {
        close(simpSessionId, CloseStatus.POLICY_VIOLATION);
    }

    /** 关闭过载连接并立即通知通道清理，浏览器可稍后重新建立连接。 */
    void overloaded(@NotNull String simpSessionId) {
        close(simpSessionId, CloseStatus.SERVICE_OVERLOAD);
    }

    /** @return 连接仍存在、尚未关闭且未被标记为拒绝新消息 */
    boolean accepts(@NotNull String simpSessionId) {
        WebSocketSession session = sessions.get(simpSessionId);
        return session != null
                && session.isOpen()
                && !Boolean.TRUE.equals(session.getAttributes().get(CLOSED_ATTRIBUTE));
    }

    /** 返回已登记连接的握手属性，供 Spring CONNECTED 事件恢复票据身份。 */
    @Nullable
    Map<String, Object> attributes(@NotNull String simpSessionId) {
        WebSocketSession session = sessions.get(simpSessionId);
        return session == null ? null : session.getAttributes();
    }

    /** 登记模块内部通道的断连清理监听。 */
    void addCloseListener(@NotNull Consumer<String> listener) {
        closeListeners.add(listener);
    }

    /** 在通道销毁时移除清理监听。 */
    void removeCloseListener(@NotNull Consumer<String> listener) {
        closeListeners.remove(listener);
    }

    /** 先禁止后续入队并释放排队引用，再请求关闭底层传输。 */
    private void close(String simpSessionId, CloseStatus status) {
        WebSocketSession session = sessions.get(simpSessionId);
        if (session == null) {
            return;
        }
        session.getAttributes().put(CLOSED_ATTRIBUTE, true);
        closeListeners.forEach(listener -> listener.accept(simpSessionId));
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException exception) {
            // Spring 的 SessionDisconnectEvent 仍会清理连接索引，日志只保留关闭失败上下文。
            LOG.debug(
                    "Web Realtime WebSocket 关闭失败 | simpSessionId={} errorType={}",
                    simpSessionId,
                    exception.getClass().getSimpleName());
        }
    }
}
