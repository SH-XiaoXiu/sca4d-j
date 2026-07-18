package com.sca4d.common.realtime.ws;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.realtime.RealtimeConnectionTargetResolver;
import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeErrorPayload;
import com.sca4d.common.realtime.RealtimeRoutes;
import com.sca4d.common.realtime.RealtimeTarget;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * STOMP 低层实时订阅授权拦截器。
 */
public class StompSubscriptionAuthorizationInterceptor implements ExecutorChannelInterceptor {

    private final RealtimeConnectionTargetResolver connectionTargetResolver;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 创建 STOMP 订阅授权拦截器。
     *
     * @param connectionTargetResolver 实时连接目标解析器
     * @param messagingTemplate STOMP 消息模板
     */
    public StompSubscriptionAuthorizationInterceptor(
            @NotNull RealtimeConnectionTargetResolver connectionTargetResolver,
            @NotNull SimpMessagingTemplate messagingTemplate) {
        this.connectionTargetResolver = connectionTargetResolver;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 对低层 realtime topic 的 SUBSCRIBE 帧执行目标授权。
     *
     * <p>业务 destination 和非订阅帧不由本拦截器处理；进入低层目标后必须存在已认证请求且目标属于当前连接可推导范围。
     *
     * @param message 当前入站 STOMP 消息
     * @param channel 执行消息处理器的入站 Channel
     * @param handler 当前消息处理器
     * @return 授权通过或不属于本拦截器范围的原始消息
     */
    @Override
    public @NotNull Message<?> beforeHandle(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            @NotNull MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(RealtimeRoutes.REALTIME_TOPIC_PREFIX)) {
            return message;
        }
        authorize(accessor, destination);
        return message;
    }

    /**
     * 使用当前请求的 Subject 和完整 Session Principal 集合完成可订阅目标精确匹配。
     *
     * @param accessor 当前订阅帧头访问器
     * @param destination 低层 realtime topic 地址
     * @throws ForbiddenException 请求未认证、目标不属于当前连接或目标格式错误时抛出
     */
    private void authorize(@NotNull StompHeaderAccessor accessor, @NotNull String destination) {
        CurrentRequest auth = CurrentRequestHolder.currentOrNull();
        if (auth == null || !auth.authenticated()) {
            throw new ForbiddenException(CommonErrorCode.REALTIME_SUBSCRIPTION_FORBIDDEN);
        }
        RealtimeTarget target = target(destination);
        if (connectionTargetResolver.resolve(auth.getSubject(), auth.getPrincipal()).contains(target)) {
            return;
        }
        for (Principal principal : auth.requireSession().getPrincipals()) {
            if (connectionTargetResolver.resolve(null, principal).contains(target)) {
                return;
            }
        }
        ForbiddenException exception = new ForbiddenException(CommonErrorCode.REALTIME_SUBSCRIPTION_FORBIDDEN);
        sendError(accessor, exception);
        throw exception;
    }

    /**
     * 从低层 STOMP topic 解析类型化实时目标，并将格式错误共用收敛为授权拒绝。
     *
     * @param destination 以 realtime topic 前缀开头的订阅地址
     * @return 订阅地址声明的实时目标
     * @throws ForbiddenException routing key 无法解析时抛出
     */
    private @NotNull RealtimeTarget target(@NotNull String destination) {
        try {
            return RealtimeTarget.fromRoutingKey(destination.substring(RealtimeRoutes.REALTIME_TOPIC_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException(CommonErrorCode.REALTIME_SUBSCRIPTION_FORBIDDEN);
        }
    }

    /**
     * 向当前已认证 STOMP 用户发送一次订阅拒绝载荷。
     *
     * @param accessor 被拒绝订阅帧的头访问器
     * @param exception 对客户端公开的授权异常
     */
    private void sendError(@NotNull StompHeaderAccessor accessor, @NotNull ForbiddenException exception) {
        java.security.Principal user = accessor.getUser();
        if (user == null) {
            return;
        }
        RealtimeErrorPayload payload = RealtimeErrorPayload.from(exception, accessor.getDestination());
        messagingTemplate.convertAndSendToUser(
                user.getName(), RealtimeRoutes.ERROR_QUEUE, RealtimeEnvelope.of("realtime.error", payload));
    }
}
