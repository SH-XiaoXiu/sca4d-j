package com.sca4d.web.realtime.stomp;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

import java.util.Objects;

/** 在 Spring 已写出会话失效事件后关闭对应 WebSocket。 */
@ApiStatus.Internal
public class StompOutboundInterceptor implements ExecutorChannelInterceptor {

    private final @NotNull WebSocketSessions sockets;

    /** 创建写出完成后的定向关闭处理器。 */
    public StompOutboundInterceptor(@NotNull WebSocketSessions sockets) {
        this.sockets = Objects.requireNonNull(sockets, "WebSocket 连接句柄不能为空");
    }

    /** 恢复经过 broker relay 返回的关闭标记，并在编码浏览器帧之前删除内部 native header。 */
    @Override
    public @NotNull Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        if (!"true".equals(
                NativeMessageHeaderAccessor.getFirstNativeHeader(
                        StompConnections.CLOSE_AFTER_HEADER,
                        message.getHeaders()))) {
            return message;
        }
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
        headers.removeNativeHeader(StompConnections.CLOSE_AFTER_HEADER);
        headers.setHeader(StompConnections.CLOSE_AFTER_HEADER, true);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders());
    }

    /** 在 Spring WebSocket transport 处理完浏览器事件后执行关闭。 */
    @Override
    public void afterMessageHandled(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            @NotNull MessageHandler handler,
            @Nullable Exception exception) {
        if (!Boolean.TRUE.equals(message.getHeaders().get(StompConnections.CLOSE_AFTER_HEADER))) {
            return;
        }
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        if (sessionId != null) {
            sockets.close(sessionId);
        }
    }
}
