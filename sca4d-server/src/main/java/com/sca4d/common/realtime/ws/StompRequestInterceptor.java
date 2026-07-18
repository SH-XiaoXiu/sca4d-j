package com.sca4d.common.realtime.ws;

import com.sca4d.common.concurrent.ratelimit.RateLimitContext;
import com.sca4d.common.concurrent.ratelimit.RateLimitContextHolder;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STOMP 帧处理期间的 CurrentRequest 与限流上下文实现。
 */
public class StompRequestInterceptor implements ExecutorChannelInterceptor {

    private static final String DESTINATION_VARIABLES_HEADER = "simpDestinationTemplateVariables";

    private final ThreadLocal<CurrentRequest> previousRequest = new ThreadLocal<>();
    private final ThreadLocal<RateLimitContext> previousRateLimit = new ThreadLocal<>();
    private final ThreadLocal<Boolean> applied = new ThreadLocal<>();

    /**
     * 从握手快照派生当前 STOMP 帧的独立请求态和限流上下文。
     *
     * <p>无法取得握手请求时保持消息原样且不写 ThreadLocal；成功应用时保存线程原值，交由
     * {@link #afterMessageHandled(Message, MessageChannel, MessageHandler, Exception)} 恢复。
     *
     * @param message 当前入站 STOMP 消息
     * @param channel 执行消息处理器的入站 Channel
     * @param handler 当前消息处理器
     * @return 未修改的原始消息
     */
    @Override
    public @NotNull Message<?> beforeHandle(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            @NotNull MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getSessionAttributes() == null) {
            return message;
        }
        Object value = accessor.getSessionAttributes()
                .get(RealtimeHandshakeInterceptor.CURRENT_REQUEST_ATTRIBUTE);
        if (!(value instanceof CurrentRequest handshakeRequest)) {
            return message;
        }

        String command = accessor.getCommand() == null
                ? "MESSAGE"
                : accessor.getCommand().name();
        String traceId = handshakeRequest.getTraceId();
        if (!StringUtils.hasText(traceId)) {
            throw new IllegalStateException("WebSocket 握手请求缺少 traceId");
        }
        String destination = accessor.getDestination();
        String path = StringUtils.hasText(destination)
                ? destination
                : handshakeRequest.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("WebSocket 握手请求缺少 path");
        }
        String clientIp = accessor.getSessionAttributes()
                .get(RealtimeHandshakeInterceptor.CLIENT_IP_ATTRIBUTE) instanceof String clientIpValue
                        ? clientIpValue
                        : handshakeRequest.getClientIp();
        String userAgent = accessor.getSessionAttributes()
                .get(RealtimeHandshakeInterceptor.USER_AGENT_ATTRIBUTE) instanceof String userAgentValue
                        ? userAgentValue
                        : null;

        CurrentRequest request = new CurrentRequest(
                traceId,
                "WS:" + command,
                path,
                clientIp,
                TimeSupport.now());
        request.setSession(handshakeRequest.getSession());
        request.setSubject(handshakeRequest.getSubject());
        request.setAccount(handshakeRequest.getAccount());
        request.setHeaders(nativeHeaders(accessor));
        request.setPathVariables(destinationVariables(message));
        request.setPayload(message.getPayload());

        previousRequest.set(CurrentRequestHolder.currentOrNull());
        previousRateLimit.set(RateLimitContextHolder.currentOrNull());
        applied.set(Boolean.TRUE);
        CurrentRequestHolder.set(request);
        RateLimitContextHolder.set(new RateLimitContext(
                "WS:" + command + ":" + path,
                clientIp,
                userAgent,
                accessor.getSessionId()));
        return message;
    }

    /**
     * 在当前帧处理完成后恢复进入拦截器前的请求态和限流上下文。
     *
     * <p>恢复过程无论业务处理成功或失败都执行，并删除本拦截器使用的全部 ThreadLocal 值，避免线程池串请求。
     *
     * @param message 已处理的 STOMP 消息
     * @param channel 执行消息处理器的入站 Channel
     * @param handler 已执行的消息处理器
     * @param exception 帧处理失败异常；成功时为 {@code null}
     */
    @Override
    public void afterMessageHandled(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            @NotNull MessageHandler handler,
            Exception exception) {
        if (!Boolean.TRUE.equals(applied.get())) {
            return;
        }
        CurrentRequest request = previousRequest.get();
        if (request == null) {
            CurrentRequestHolder.clear();
        } else {
            CurrentRequestHolder.set(request);
        }
        RateLimitContext rateLimit = previousRateLimit.get();
        if (rateLimit == null) {
            RateLimitContextHolder.clear();
        } else {
            RateLimitContextHolder.set(rateLimit);
        }
        previousRequest.remove();
        previousRateLimit.remove();
        applied.remove();
    }

    /**
     * 复制当前帧的 native headers，供 Principal SpEL 和业务方法读取。
     *
     * @param accessor 当前 STOMP 帧头访问器
     * @return 不再引用 STOMP 消息对象的有序 header 映射
     */
    private @NotNull Map<String, Object> nativeHeaders(
            @NotNull StompHeaderAccessor accessor) {
        return new LinkedHashMap<>(accessor.toNativeHeaderMap());
    }

    /**
     * 读取 Spring 在目标模板匹配后写入的 destination path 变量。
     *
     * @param message 当前入站 STOMP 消息
     * @return 键共用转换为字符串的 path 变量；消息没有模板变量时返回空映射
     */
    private @NotNull Map<String, Object> destinationVariables(
            @NotNull Message<?> message) {
        Object value = message.getHeaders().get(DESTINATION_VARIABLES_HEADER);
        if (!(value instanceof Map<?, ?> variables)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        variables.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
