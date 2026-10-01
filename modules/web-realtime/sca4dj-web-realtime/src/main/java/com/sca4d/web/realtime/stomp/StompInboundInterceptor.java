package com.sca4d.web.realtime.stomp;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 校验 Spring STOMP 入站命令，并在 SUBSCRIBE、SEND 处理期间绑定独立 Call。 */
@ApiStatus.Internal
public class StompInboundInterceptor implements ExecutorChannelInterceptor {

    private static final String CALL_HEADER = StompInboundInterceptor.class.getName() + ".call";

    private final @NotNull RedisConnectionRegistry shared;

    private final @Nullable CallInvoker invoker;

    private final @NotNull Clock clock;

    private final @NotNull List<@NotNull WebRealtimeSubscriptionPolicy> subscriptionPolicies;

    private final @NotNull StompRateLimits rateLimits;

    /** 创建 Spring STOMP 入站检查器。 */
    public StompInboundInterceptor(
            @NotNull StompRateLimits rateLimits,
            @NotNull RedisConnectionRegistry shared,
            @NotNull List<@NotNull WebRealtimeSubscriptionPolicy> subscriptionPolicies,
            @NotNull Clock clock) {
        this(rateLimits, shared, subscriptionPolicies, clock, null);
    }

    /** 创建绑定统一生命周期执行器的 Spring STOMP 入站检查器。 */
    public StompInboundInterceptor(
            @NotNull StompRateLimits rateLimits,
            @NotNull RedisConnectionRegistry shared,
            @NotNull List<@NotNull WebRealtimeSubscriptionPolicy> subscriptionPolicies,
            @NotNull Clock clock,
            @Nullable CallInvoker invoker) {
        this.rateLimits = Objects.requireNonNull(rateLimits, "Web Realtime 限流策略不能为空");
        this.shared = Objects.requireNonNull(shared, "Web Realtime 共享索引不能为空");
        this.subscriptionPolicies = List.copyOf(subscriptionPolicies);
        this.clock = Objects.requireNonNull(clock, "Web Realtime 时钟不能为空");
        this.invoker = invoker;
    }

    /** 在 Spring 分派命令前检查身份、STOMP 1.2、限流和动态订阅策略。 */
    @Override
    public @NotNull Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        StompHeaderAccessor input = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (input == null || input.getCommand() == null) {
            return message;
        }
        StompCommand command = input.getCommand();
        Session session = session(input);

        // CONNECT 只确认握手身份、协议版本、Session 有效期和共享限额，不读取 STOMP 登录凭据。
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            validateConnect(input, session);
            return message;
        }
        if (command == StompCommand.DISCONNECT) {
            return message;
        }
        ConnectionId connectionId = connectionId(input);

        // UNSUBSCRIBE 只删除已授权的动态目标；固定用户事件订阅不进入共享动态索引。
        if (command == StompCommand.UNSUBSCRIBE) {
            shared.unsubscribe(connectionId, requiredText(input.getSubscriptionId(), "STOMP subscription id"));
            return message;
        }
        if (command != StompCommand.SUBSCRIBE && command != StompCommand.SEND) {
            return message;
        }
        validateSession(session);

        // SUBSCRIBE 使用当前帧独立 Call 完成授权并登记动态目标。
        if (command == StompCommand.SUBSCRIBE) {
            if (!rateLimits.subscribe(session.subject().subjectId(), connectionId.value())) {
                throw new IllegalStateException("STOMP SUBSCRIBE 超过限流");
            }
            Call call = newCall(input, session);
            assessSubscription(input, connectionId, call);
            return withCall(message, call);
        }

        // SEND 只进入具体应用的 /app/** 处理器，并把独立 Call 绑定到异步处理线程。
        if (!rateLimits.send(session.subject().subjectId(), connectionId.value())) {
            throw new IllegalStateException("STOMP SEND 超过限流");
        }
        String destination = requiredText(input.getDestination(), "STOMP destination");
        if (!destination.startsWith(WebRealtimeProtocol.APPLICATION_DESTINATION_PREFIX + "/")) {
            throw new IllegalArgumentException("STOMP SEND 只允许 /app/** destination");
        }
        return withCall(message, newCall(input, session));
    }

    /** 在执行异步消息处理器之前把本帧独有的 Call 绑定到当前线程。 */
    @Override
    public @NotNull Message<?> beforeHandle(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            @NotNull MessageHandler handler) {
        Object savedCall = message.getHeaders().get(CALL_HEADER);
        if (savedCall instanceof Call call) {
            CallHolder.set(call);
        }
        return message;
    }

    /** 在任一 Spring 消息处理器结束后清理 Call ThreadLocal。 */
    @Override
    public void afterMessageHandled(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            @NotNull MessageHandler handler,
            @Nullable Exception exception) {
        if (message.getHeaders().containsKey(CALL_HEADER)) {
            CallHolder.clear();
        }
    }

    /** 确认握手身份、唯一 STOMP 1.2 版本和 IP CONNECT 额度。 */
    private void validateConnect(
            @NotNull StompHeaderAccessor accessor,
            @NotNull Session session) {
        Set<String> versions = accessor.getAcceptVersion();
        if (versions.size() != 1 || !WebRealtimeProtocol.STOMP_VERSION.equals(versions.iterator().next())) {
            throw new IllegalArgumentException("Web Realtime 只接受 STOMP 1.2");
        }
        validateSession(session);
        Map<String, Object> attributes = accessor.getSessionAttributes();
        String ip = attributes == null
                ? "unknown"
                : Objects.toString(attributes.get(TicketHandshakeInterceptor.IP_ATTRIBUTE), "unknown");
        if (!rateLimits.connect(ip)) {
            throw new IllegalStateException("STOMP CONNECT 超过限流");
        }
    }

    /** 校验固定用户事件订阅或按唯一 App 策略登记 Resource、Topic 订阅。 */
    private void assessSubscription(
            @NotNull StompHeaderAccessor accessor,
            @NotNull ConnectionId connectionId,
            @NotNull Call call) {
        String destination = requiredText(accessor.getDestination(), "STOMP destination");
        if (WebRealtimeProtocol.USER_EVENT_DESTINATION.equals(destination)) {
            return;
        }
        String subscriptionId = requiredText(accessor.getSubscriptionId(), "STOMP subscription id");
        List<WebRealtimeSubscriptionPolicy> matched = subscriptionPolicies.stream()
                .filter(policy -> policy.supports(destination))
                .toList();
        if (matched.size() != 1) {
            throw new IllegalStateException("STOMP destination 必须由唯一订阅策略负责");
        }
        try {
            CallHolder.set(call);
            WebRealtimeTarget target = matched.getFirst()
                    .authorize(call, destination);
            if (!shared.subscribe(connectionId, subscriptionId, target)) {
                throw new IllegalStateException("Web Realtime 连接共享状态已经失效");
            }
        } finally {
            CallHolder.clear();
        }
    }

    /** 创建携带当前 STOMP 帧 Call 的新 Spring 消息。 */
    private @NotNull Message<?> withCall(
            @NotNull Message<?> message,
            @NotNull Call call) {
        StompHeaderAccessor copy = StompHeaderAccessor.wrap(message);
        copy.setHeader(CALL_HEADER, call);
        copy.setLeaveMutable(true);
        return MessageBuilder.createMessage(message.getPayload(), copy.getMessageHeaders());
    }

    /** 创建仅属于当前 STOMP 帧的 Call，并选择 native header 指定的业务主体。 */
    private @NotNull Call newCall(
            @NotNull StompHeaderAccessor accessor,
            @NotNull Session session) {
        List<String> values = accessor.getNativeHeader(WebRealtimeProtocol.PRINCIPAL_ID_HEADER);
        if (values == null || values.size() != 1) {
            throw new IllegalArgumentException("STOMP principal-id 必须且只能提供一次");
        }
        String value = requiredText(values.getFirst(), "STOMP principal-id");
        long principalId;
        try {
            principalId = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("STOMP principal-id 必须为正整数", exception);
        }
        if (principalId <= 0) {
            throw new IllegalArgumentException("STOMP principal-id 必须为正整数");
        }
        Principal principal = session.principalById(principalId);
        Call call = new Call(
                invoker,
                UUID.randomUUID().toString(),
                clock.instant(),
                com.sca4d.core.api.data.DataValues.builder().build(),
                null);
        call.authenticate(session);
        call.selectPrincipal(principal);
        return call;
    }

    /** 拒绝票据握手后已经过期的 Session。 */
    private void validateSession(@NotNull Session session) {
        if (!session.expiresAt().isAfter(clock.instant())) {
            throw new IllegalStateException("STOMP 握手绑定的 Session 已失效");
        }
    }

    /** 返回一次性票据在 WebSocket 握手属性中绑定的权威 Session。 */
    private static @NotNull Session session(@NotNull StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object value = attributes == null
                ? null
                : attributes.get(TicketHandshakeInterceptor.SESSION_ATTRIBUTE);
        if (!(value instanceof Session session)) {
            throw new IllegalStateException("STOMP 连接没有票据握手身份");
        }
        return session;
    }

    /** 返回 Spring Session 属性中已经登记的 Web Realtime ConnectionId。 */
    private static @NotNull ConnectionId connectionId(@NotNull StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object value = attributes == null ? null : attributes.get(StompConnections.CONNECTION_ATTRIBUTE);
        if (!(value instanceof ConnectionId connectionId)) {
            throw new IllegalStateException("STOMP 连接尚未完成 Web Realtime 登记");
        }
        return connectionId;
    }

    /** 校验 STOMP 必填文本 header。 */
    private static @NotNull String requiredText(@Nullable String value, @NotNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
