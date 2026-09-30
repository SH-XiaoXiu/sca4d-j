package com.sca4d.web.realtime.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.ConfiguredConstants;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import com.sca4d.web.realtime.ticket.TicketHandshakeInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证 Spring STOMP 入站命令的协议、订阅处理和 Call 绑定。
 */
class StompInboundInterceptorTest {

    @BeforeAll
    static void initializeProtocol() {
        ConfiguredConstants.initialize(WebRealtimeProtocol.class, ignored -> null);
    }

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    private static final Clock LOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /**
     * 清理失败断言可能留下的 Call，避免影响后续测试。
     */
    @AfterEach
    void clearCall() {
        CallHolder.clear();
    }

    @Test
    void acceptsOnlySingleStomp12VersionAndAppliesConnectLimit() {
        Session session = session();
        StompInboundInterceptor accepted = interceptor(
                (key, limit) -> RateLimitDecision.allow(),
                mock(RedisConnectionRegistry.class),
                List.of());

        assertThat(accepted.preSend(connect(session, "1.2"), mock(MessageChannel.class))).isNotNull();
        assertThatThrownBy(
                () -> accepted.preSend(
                        connect(session, "1.1,1.2"),
                        mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Web Realtime 只接受 STOMP 1.2");

        StompInboundInterceptor limited = interceptor(
                (key, limit) -> RateLimitDecision.deny(Duration.ofSeconds(1)),
                mock(RedisConnectionRegistry.class),
                List.of());
        assertThatThrownBy(() -> limited.preSend(connect(session, "1.2"), mock(MessageChannel.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STOMP CONNECT 超过限流");
    }

    @Test
    void rejectsExpiredSessionOnConnectAndBusinessFrames() {
        Session expired = session(NOW.minus(Duration.ofHours(2)), NOW);
        StompInboundInterceptor interceptor = interceptor(
                (key, limit) -> RateLimitDecision.allow(),
                mock(RedisConnectionRegistry.class),
                List.of());
        MessageChannel channel = mock(MessageChannel.class);

        assertThatThrownBy(() -> interceptor.preSend(connect(expired, "1.2"), channel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STOMP 握手绑定的 Session 已失效");
        assertThatThrownBy(
                () -> interceptor.preSend(
                        command(
                                expired,
                                new ConnectionId("expired-connection"),
                                StompCommand.SEND,
                                "/app/invoices",
                                null),
                        channel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STOMP 握手绑定的 Session 已失效");
    }

    @Test
    void authorizesDynamicSubscriptionWithIndependentCallAndRegistersTarget() {
        Session session = session();
        ConnectionId connectionId = new ConnectionId("connection-subscribe");
        RedisConnectionRegistry shared = mock(RedisConnectionRegistry.class);
        WebRealtimeSubscriptionPolicy policy = mock(WebRealtimeSubscriptionPolicy.class);
        WebRealtimeTarget target = WebRealtimeTarget.topic("invoice:42");
        AtomicReference<Call> authorized = new AtomicReference<>();
        when(policy.supports("/topic/invoices/42")).thenReturn(true);
        when(policy.authorize(any(Call.class), eq("/topic/invoices/42")))
                .thenAnswer(invocation -> {
                    Call call = invocation.getArgument(0);
                    authorized.set(call);
                    assertThat(call.session()).containsSame(session);
                    assertThat(call.principal())
                            .hasValueSatisfying(principal -> assertThat(principal.principalId()).isEqualTo(31));
                    return target;
                });
        when(shared.subscribe(connectionId, "invoice-42", target)).thenReturn(true);
        StompInboundInterceptor interceptor = interceptor(
                (key, limit) -> RateLimitDecision.allow(),
                shared,
                List.of(policy));

        Message<?> result = interceptor.preSend(
                command(session, connectionId, StompCommand.SUBSCRIBE, "/topic/invoices/42", "invoice-42"),
                mock(MessageChannel.class));

        assertThat(result).isNotNull();
        assertThat(CallHolder.currentOrNull()).isNull();
        MessageChannel channel = mock(MessageChannel.class);
        MessageHandler handler = mock(MessageHandler.class);
        interceptor.beforeHandle(result, channel, handler);
        assertThat(CallHolder.current()).isSameAs(authorized.get());
        interceptor.afterMessageHandled(result, channel, handler, null);
        assertThat(CallHolder.currentOrNull()).isNull();
        verify(policy).authorize(any(Call.class), eq("/topic/invoices/42"));
        verify(shared).subscribe(connectionId, "invoice-42", target);
    }

    @Test
    void bindsSendCallOnlyForApplicationDestinationAndClearsItAfterHandling() {
        Session session = session();
        ConnectionId connectionId = new ConnectionId("connection-send");
        StompInboundInterceptor interceptor = interceptor(
                (key, limit) -> RateLimitDecision.allow(),
                mock(RedisConnectionRegistry.class),
                List.of());
        MessageChannel channel = mock(MessageChannel.class);
        MessageHandler handler = mock(MessageHandler.class);

        assertThatThrownBy(
                () -> interceptor.preSend(
                        command(session, connectionId, StompCommand.SEND, "/topic/invoices", null),
                        channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STOMP SEND 只允许 /app/** destination");

        Message<?> checked = Objects.requireNonNull(
                interceptor.preSend(
                        command(session, connectionId, StompCommand.SEND, "/app/invoices", null),
                        channel));
        interceptor.beforeHandle(checked, channel, handler);
        assertThat(CallHolder.current().session()).containsSame(session);
        assertThat(CallHolder.current().principal())
                .hasValueSatisfying(principal -> assertThat(principal.principalId()).isEqualTo(31));

        interceptor.afterMessageHandled(checked, channel, handler, null);
        assertThat(CallHolder.currentOrNull()).isNull();
    }

    @Test
    void rejectsMissingDuplicateInvalidAndUnavailablePrincipalIds() {
        Session session = session();
        ConnectionId connectionId = new ConnectionId("connection-principal");
        StompInboundInterceptor interceptor = interceptor(
                (key, limit) -> RateLimitDecision.allow(),
                mock(RedisConnectionRegistry.class),
                List.of());
        MessageChannel channel = mock(MessageChannel.class);

        assertThatThrownBy(
                () -> interceptor.preSend(
                        command(session, connectionId, StompCommand.SEND, "/app/invoices", null, List.of()),
                        channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STOMP principal-id 必须且只能提供一次");
        assertThatThrownBy(
                () -> interceptor.preSend(
                        command(session, connectionId, StompCommand.SEND, "/app/invoices", null, List.of("31", "32")),
                        channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STOMP principal-id 必须且只能提供一次");
        assertThatThrownBy(
                () -> interceptor.preSend(
                        command(session, connectionId, StompCommand.SEND, "/app/invoices", null, List.of("principal")),
                        channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STOMP principal-id 必须为正整数");
        assertThatThrownBy(
                () -> interceptor.preSend(
                        command(session, connectionId, StompCommand.SEND, "/app/invoices", null, List.of("0")),
                        channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STOMP principal-id 必须为正整数");
        assertThatThrownBy(
                () -> interceptor.preSend(
                        command(session, connectionId, StompCommand.SEND, "/app/invoices", null, List.of("99")),
                        channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Principal 不属于当前 Session: 99");
    }

    /**
     * 创建使用指定共享限流结果和订阅策略的入站拦截器。
     */
    private static StompInboundInterceptor interceptor(
            RateLimiter limiter,
            RedisConnectionRegistry shared,
            List<WebRealtimeSubscriptionPolicy> policies) {
        return new StompInboundInterceptor(
                new StompRateLimits(limiter),
                shared,
                policies,
                LOCK);
    }

    /**
     * 创建包含握手身份、来源 IP 和 accept-version 的 CONNECT 消息。
     */
    private static Message<?> connect(Session session, String versions) {
        StompHeaderAccessor headers = headers(session, StompCommand.CONNECT);
        headers.setAcceptVersion(versions);
        Objects.requireNonNull(headers.getSessionAttributes())
                .put(TicketHandshakeInterceptor.IP_ATTRIBUTE, "192.0.2.10");
        return message(headers);
    }

    /**
     * 创建已经登记 ConnectionId 的 SUBSCRIBE 或 SEND 消息。
     */
    private static Message<?> command(
            Session session,
            ConnectionId connectionId,
            StompCommand command,
            String destination,
            String subscriptionId) {
        return command(session, connectionId, command, destination, subscriptionId, List.of("31"));
    }

    /**
     * 创建包含指定业务主体 header 的 SUBSCRIBE 或 SEND 消息。
     */
    private static Message<?> command(
            Session session,
            ConnectionId connectionId,
            StompCommand command,
            String destination,
            String subscriptionId,
            List<String> principalIds) {
        StompHeaderAccessor headers = headers(session, command);
        Objects.requireNonNull(headers.getSessionAttributes())
                .put(StompConnections.CONNECTION_ATTRIBUTE, connectionId);
        headers.setDestination(destination);
        if (subscriptionId != null) {
            headers.setSubscriptionId(subscriptionId);
        }
        principalIds.forEach(value -> headers.addNativeHeader(WebRealtimeProtocol.PRINCIPAL_ID_HEADER, value));
        return message(headers);
    }

    /**
     * 创建具有独立 Spring Session 属性和票据握手 Session 的 STOMP header。
     */
    private static StompHeaderAccessor headers(Session session, StompCommand command) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("stomp-session");
        headers.setSessionAttributes(new HashMap<>());
        Objects.requireNonNull(headers.getSessionAttributes())
                .put(TicketHandshakeInterceptor.SESSION_ATTRIBUTE, session);
        headers.setLeaveMutable(true);
        return headers;
    }

    /**
     * 把 Spring STOMP header 包装为入站消息。
     */
    private static Message<?> message(StompHeaderAccessor headers) {
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    /**
     * 创建测试使用的有效 Session。
     */
    private static Session session() {
        return session(NOW, NOW.plus(Duration.ofHours(1)));
    }

    /**
     * 创建使用指定有效时间的 Session。
     */
    private static Session session(Instant issuedAt, Instant expiresAt) {
        Subject subject = new Subject(11, SubjectType.HUMAN);
        Account account = new Account(21, subject.subjectId(), new RealmKey("test"));
        Principal principal = () -> 31;
        return new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(principal),
                List.of(),
                List.of(
                        new Authentication(
                                account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/TEST"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE),
                                issuedAt)),
                Map.of(principal.principalId(), List.of()),
                Map.of(),
                issuedAt,
                expiresAt);
    }
}
