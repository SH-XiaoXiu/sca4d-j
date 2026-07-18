package com.sca4d.common.realtime.ws;

import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeErrorPayload;
import com.sca4d.common.realtime.RealtimeRoutes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * STOMP 业务异常处理器。
 */
@ControllerAdvice
public class RealtimeMessagingExceptionHandler {

    /**
     * 处理限流异常。
     *
     * @param exception 限流异常
     * @param message 当前 STOMP 消息
     * @return 用户错误队列载荷
     */
    @MessageExceptionHandler(RateLimitException.class)
    @SendToUser(RealtimeRoutes.ERROR_QUEUE)
    public @NotNull RealtimeEnvelope<RealtimeErrorPayload> handleRateLimit(
            @NotNull RateLimitException exception, @NotNull Message<?> message) {
        return RealtimeEnvelope.of("realtime.error", RealtimeErrorPayload.from(exception, destination(message)));
    }

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @param message 当前 STOMP 消息
     * @return 用户错误队列载荷
     */
    @MessageExceptionHandler(BizException.class)
    @SendToUser(RealtimeRoutes.ERROR_QUEUE)
    public @NotNull RealtimeEnvelope<RealtimeErrorPayload> handleBizException(
            @NotNull BizException exception, @NotNull Message<?> message) {
        return RealtimeEnvelope.of("realtime.error", RealtimeErrorPayload.from(exception, destination(message)));
    }

    /**
     * 提取发生异常的 STOMP 目标地址，供客户端定位失败操作。
     *
     * @param message 触发异常的 STOMP 消息
     * @return 当前 destination；消息没有 STOMP 访问器时返回 {@code null}
     */
    private @Nullable String destination(@NotNull Message<?> message) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        return accessor == null ? null : accessor.getDestination();
    }
}
