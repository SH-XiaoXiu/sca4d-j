package com.sca4d.common.realtime.ws;

import com.sca4d.common.concurrent.ratelimit.RateLimitContext;
import com.sca4d.common.concurrent.ratelimit.RateLimitContextHolder;
import com.sca4d.common.concurrent.ratelimit.RateLimitException;
import com.sca4d.common.concurrent.ratelimit.RateLimitKeyGenerator;
import com.sca4d.common.concurrent.ratelimit.RateLimitRule;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.common.concurrent.ratelimit.RateLimiter;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeErrorPayload;
import com.sca4d.common.realtime.RealtimeRoutes;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;

/**
 * STOMP 帧级限流拦截器。
 */
public class StompFrameRateLimitInterceptor implements ExecutorChannelInterceptor {

    private final RateLimiter rateLimiter;
    private final RateLimitKeyGenerator keyGenerator;
    private final Sca4djConcurrentProperties concurrentProperties;
    private final Sca4djRealtimeProperties realtimeProperties;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 创建 STOMP 帧级限流拦截器。
     *
     * @param rateLimiter 限流器
     * @param keyGenerator 限流 key 生成器
     * @param concurrentProperties 并发控制配置
     * @param realtimeProperties 实时通信配置
     * @param messagingTemplate STOMP 消息模板
     */
    public StompFrameRateLimitInterceptor(
            @NotNull RateLimiter rateLimiter,
            @NotNull RateLimitKeyGenerator keyGenerator,
            @NotNull Sca4djConcurrentProperties concurrentProperties,
            @NotNull Sca4djRealtimeProperties realtimeProperties,
            @NotNull SimpMessagingTemplate messagingTemplate) {
        this.rateLimiter = rateLimiter;
        this.keyGenerator = keyGenerator;
        this.concurrentProperties = concurrentProperties;
        this.realtimeProperties = realtimeProperties;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 对启用限流的 CONNECT、SUBSCRIBE 和 SEND 帧消费对应共享限流桶。
     *
     * <p>限流拒绝时先尽力向已认证用户发送一次错误帧，再重新抛出异常终止当前帧处理；未配置命令或上下文时不消费限流器。
     *
     * @param message 当前入站 STOMP 消息
     * @param channel 执行消息处理器的入站 Channel
     * @param handler 当前消息处理器
     * @return 限流允许后的原始消息
     */
    @Override
    public @NotNull Message<?> beforeHandle(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            @NotNull MessageHandler handler) {
        if (!concurrentProperties.getRateLimit().isEnabled()
                || !realtimeProperties.getWebsocket().getFrameRateLimit().isEnabled()) {
            return message;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        RateLimitContext context = RateLimitContextHolder.currentOrNull();
        if (context == null) {
            return message;
        }
        try {
            check(accessor.getCommand(), context);
        } catch (RateLimitException exception) {
            sendError(accessor, exception);
            throw exception;
        }
        return message;
    }

    /**
     * 按 STOMP 命令选择限流维度和固定消费次数。
     *
     * <p>CONNECT 消费 1 个 IP 桶；SUBSCRIBE 和 SEND 各顺序消费 1 个 USER 桶与 1 个 CONNECTION 桶；其他命令不消费。
     *
     * @param command 当前 STOMP 命令
     * @param context 当前帧的限流维度数据
     */
    private void check(@NotNull StompCommand command, @NotNull RateLimitContext context) {
        if (command == StompCommand.CONNECT) {
            consume(RateLimitType.IP, context, realtimeProperties.getWebsocket().getFrameRateLimit().getConnectCapacity());
            return;
        }
        if (command == StompCommand.SUBSCRIBE) {
            long capacity = realtimeProperties.getWebsocket().getFrameRateLimit().getSubscribeCapacity();
            consume(RateLimitType.USER, context, capacity);
            consume(RateLimitType.CONNECTION, context, capacity);
            return;
        }
        if (command == StompCommand.SEND) {
            long capacity = realtimeProperties.getWebsocket().getFrameRateLimit().getSendCapacity();
            consume(RateLimitType.USER, context, capacity);
            consume(RateLimitType.CONNECTION, context, capacity);
        }
    }

    /**
     * 使用当前帧配置构造一个限流规则，并执行一次共享限流器消费。
     *
     * @param type 本次消费使用的限流维度
     * @param context 当前帧的限流维度数据
     * @param capacity 一个时间窗口允许的容量和补充数量
     * @throws RateLimitException 共享限流器拒绝本次消费时抛出
     */
    private void consume(@NotNull RateLimitType type, @NotNull RateLimitContext context, long capacity) {
        RateLimitRule rule = new RateLimitRule(
                capacity, capacity, realtimeProperties.getWebsocket().getFrameRateLimit().getWindow());
        String key = keyGenerator.generate(type, context, null);
        var result = rateLimiter.tryConsume(key, rule);
        if (!result.isAllowed()) {
            throw new RateLimitException(CommonErrorCode.TOO_MANY_REQUESTS, result.getRetryAfterSeconds());
        }
    }

    /**
     * 向当前已认证 STOMP 用户发送一次限流错误载荷。
     *
     * @param accessor 被拒绝帧的头访问器
     * @param exception 包含客户端重试秒数的限流异常
     */
    private void sendError(@NotNull StompHeaderAccessor accessor, @NotNull RateLimitException exception) {
        Principal user = accessor.getUser();
        if (user == null) {
            return;
        }
        RealtimeErrorPayload payload = RealtimeErrorPayload.from(exception, accessor.getDestination());
        messagingTemplate.convertAndSendToUser(
                user.getName(), RealtimeRoutes.ERROR_QUEUE, RealtimeEnvelope.of("realtime.error", payload));
    }
}
