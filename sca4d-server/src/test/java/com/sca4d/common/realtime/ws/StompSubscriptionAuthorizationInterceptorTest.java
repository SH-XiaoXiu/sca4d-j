package com.sca4d.common.realtime.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.realtime.DefaultRealtimeConnectionTargetResolver;
import com.sca4d.common.realtime.RealtimeRoutes;
import com.sca4d.common.realtime.RealtimeTarget;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.test.TestSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Map;

/** 验证 STOMP 订阅目标必须与当前 Subject 或 Principal 匹配。 */
class StompSubscriptionAuthorizationInterceptorTest {

    private final StompSubscriptionAuthorizationInterceptor interceptor = new StompSubscriptionAuthorizationInterceptor(
            new DefaultRealtimeConnectionTargetResolver(), mock(SimpMessagingTemplate.class));

    @AfterEach
    void tearDown() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldAllowCurrentSubjectRealtimeTopic() {
        CurrentRequest auth = CurrentRequest.authenticated(TestSession.of(7L));
        CurrentRequestHolder.set(auth);

        interceptor.beforeHandle(
                message(RealtimeRoutes.realtimeTopic("subject.7")), mock(MessageChannel.class), mock(MessageHandler.class));
    }

    @Test
    void shouldRejectOtherSubjectRealtimeTopic() {
        CurrentRequest auth = CurrentRequest.authenticated(TestSession.of(7L));
        CurrentRequestHolder.set(auth);

        assertThatThrownBy(() -> interceptor.beforeHandle(
                message(RealtimeRoutes.realtimeTopic("subject.8")), mock(MessageChannel.class), mock(MessageHandler.class)))
                .isInstanceOfSatisfying(ForbiddenException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.realtime.subscription-forbidden"));
    }

    @Test
    void shouldAllowPrincipalAndTenantTopicsOwnedByAuthenticatedSession() {
        Principal principal = Principal.of(21L, PrincipalType.TENANT, 7L, 88L);
        CurrentRequest auth = CurrentRequest.authenticated(TestSession.withAuthorization(
                TestSession.of(7L).getSubject(), List.of(principal), Map.of()));
        CurrentRequestHolder.set(auth);

        interceptor.beforeHandle(
                message(RealtimeTarget.principal(PrincipalType.TENANT, 21L).stompDestination()),
                mock(MessageChannel.class),
                mock(MessageHandler.class));
        interceptor.beforeHandle(
                message(RealtimeTarget.tenant(88L).stompDestination()),
                mock(MessageChannel.class),
                mock(MessageHandler.class));
    }

    @Test
    void shouldRejectPrincipalAndTenantTopicsOutsideAuthenticatedSession() {
        Principal principal = Principal.of(21L, PrincipalType.TENANT, 7L, 88L);
        CurrentRequest auth = CurrentRequest.authenticated(TestSession.withAuthorization(
                TestSession.of(7L).getSubject(), List.of(principal), Map.of()));
        CurrentRequestHolder.set(auth);

        assertThatThrownBy(() -> interceptor.beforeHandle(
                message(RealtimeTarget.principal(PrincipalType.TENANT, 22L).stompDestination()),
                mock(MessageChannel.class),
                mock(MessageHandler.class)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> interceptor.beforeHandle(
                message(RealtimeTarget.tenant(89L).stompDestination()),
                mock(MessageChannel.class),
                mock(MessageHandler.class)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldIgnoreBusinessUserQueue() {
        interceptor.beforeHandle(message("/user/queue/im"), mock(MessageChannel.class), mock(MessageHandler.class));
    }

    /**
     * 创建携带固定会话和目标地址的 STOMP SUBSCRIBE 帧。
     *
     * @param destination 待订阅目标地址
     * @return 可直接传给订阅授权拦截器的消息
     */
    private @NotNull Message<byte[]> message(@NotNull String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-1");
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
