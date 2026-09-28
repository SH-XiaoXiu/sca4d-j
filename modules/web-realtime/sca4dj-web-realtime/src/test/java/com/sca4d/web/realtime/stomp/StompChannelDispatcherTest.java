package com.sca4d.web.realtime.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.web.realtime.config.WebRealtimeProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 用真实 Spring 通道验证顺序、背压、失败和断连后的容量回收。 */
class StompChannelDispatcherTest {

    @Test
    void serializesOneConnectionWhileAnotherCanRun() throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        peer(sockets, "a");
        peer(sockets, "b");
        WebRealtimeProperties.Stomp limits = new WebRealtimeProperties.Stomp();
        limits.setChannelWorkerCount(2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        CountDownLatch other = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        List<Integer> order = new CopyOnWriteArrayList<>();
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher("test", limits, sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.subscribe(message -> {
                if (session(message).equals("b")) {
                    other.countDown();
                    return;
                }
                int value = ((byte[]) message.getPayload())[0];
                if (value == 1) {
                    entered.countDown();
                    await(unblock);
                }
                order.add(value);
                done.countDown();
            });
            try {
                channel.send(message("a", 1));
                await(entered);
                channel.send(message("a", 2));
                channel.send(message("a", 3));
                channel.send(message("b", 4));
                await(other);
                assertThat(order).isEmpty();
                unblock.countDown();
                await(done);
                assertThat(order).containsExactly(1, 2, 3);
            } finally {
                unblock.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"connection-count", "connection-bytes", "global-count", "global-bytes"})
    void rejectsOverloadBeforeExecutionAndReleasesDiscardedWork(String limit) throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        AtomicReference<CloseStatus> closed = peer(sockets, "a");
        peer(sockets, "b");
        WebRealtimeProperties.Stomp limits = new WebRealtimeProperties.Stomp();
        limits.setChannelWorkerCount(1);
        switch (limit) {
            case "connection-count" -> limits.setConnectionQueueCapacity(2);
            case "connection-bytes" -> limits.setConnectionQueueMaxBytes(2);
            case "global-count" -> limits.setChannelQueueCapacity(2);
            case "global-bytes" -> limits.setChannelQueueMaxBytes(2);
            default -> throw new AssertionError(limit);
        }
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        CountDownLatch other = new CountDownLatch(1);
        List<Integer> handled = new CopyOnWriteArrayList<>();
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher("test", limits, sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.subscribe(message -> {
                int value = ((byte[]) message.getPayload())[0];
                if (value == 1) {
                    entered.countDown();
                    await(unblock);
                }
                handled.add(value);
                if (session(message).equals("b")) {
                    other.countDown();
                }
            });
            try {
                channel.send(message("a", 1));
                await(entered);
                channel.send(message("a", 2));
                assertThatThrownBy(() -> channel.send(message("a", 3)))
                        .isInstanceOf(MessageDeliveryException.class);
                assertThat(closed.get()).isEqualTo(CloseStatus.SERVICE_OVERLOAD);
                // a 的第二条消息已清理，另一条连接可以使用释放的容量。
                channel.send(message("b", 4));
                unblock.countDown();
                await(other);
                assertThat(handled).containsExactly(1, 4);
            } finally {
                unblock.countDown();
            }
        }
    }

    @Test
    void countsNonAsciiTextConservatively() throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        AtomicReference<CloseStatus> closed = peer(sockets, "text");
        WebRealtimeProperties.Stomp limits = new WebRealtimeProperties.Stomp();
        limits.setConnectionQueueMaxBytes(2);
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher("test", limits, sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.subscribe(message -> {
                throw new AssertionError("超限文本不得执行");
            });
            SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId("text");
            Message<String> text = MessageBuilder.createMessage("\u0800", headers.getMessageHeaders());
            assertThatThrownBy(() -> channel.send(text)).isInstanceOf(MessageDeliveryException.class);
            assertThat(closed.get()).isEqualTo(CloseStatus.SERVICE_OVERLOAD);
        }
    }

    @Test
    void reconnectChurnReleasesWorkerQueueSlots() throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        peer(sockets, "busy");
        WebRealtimeProperties.Stomp limits = new WebRealtimeProperties.Stomp();
        limits.setChannelWorkerCount(1);
        limits.setChannelQueueCapacity(2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher("test", limits, sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.subscribe(message -> {
                if (session(message).equals("busy")) {
                    entered.countDown();
                    await(unblock);
                } else {
                    delivered.countDown();
                }
            });
            try {
                channel.send(message("busy", 1));
                await(entered);
                for (int index = 0; index < 10; index++) {
                    String id = "reconnect-" + index;
                    peer(sockets, id);
                    assertThat(channel.send(message(id, 2))).isTrue();
                    sockets.close(id);
                }
                peer(sockets, "survivor");
                assertThat(channel.send(message("survivor", 3))).isTrue();
                unblock.countDown();
                await(delivered);
            } finally {
                unblock.countDown();
            }
        }
    }

    @Test
    void accountsGlobalBudgetAcrossConnections() throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        peer(sockets, "a");
        AtomicReference<CloseStatus> closed = peer(sockets, "b");
        WebRealtimeProperties.Stomp limits = new WebRealtimeProperties.Stomp();
        limits.setChannelQueueCapacity(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher("test", limits, sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.subscribe(message -> {
                entered.countDown();
                await(unblock);
            });
            try {
                channel.send(message("a", 1));
                await(entered);
                assertThatThrownBy(() -> channel.send(message("b", 2)))
                        .isInstanceOf(MessageDeliveryException.class);
                assertThat(closed.get()).isEqualTo(CloseStatus.SERVICE_OVERLOAD);
            } finally {
                unblock.countDown();
            }
        }
    }

    @Test
    void failedInterceptorReleasesReservationAndSubmissionContext() throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        peer(sockets, "a");
        WebRealtimeProperties.Stomp limits = new WebRealtimeProperties.Stomp();
        limits.setChannelQueueCapacity(1);
        CountDownLatch handled = new CountDownLatch(1);
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher("test", limits, sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.addInterceptor(new ChannelInterceptor() {
                @Override
                public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel ignored) {
                    if (((byte[]) message.getPayload())[0] == 1) {
                        throw new IllegalArgumentException("拒绝输入");
                    }
                    return message;
                }
            });
            channel.subscribe(message -> handled.countDown());
            assertThatThrownBy(() -> channel.send(message("a", 1)))
                    .isInstanceOf(MessageDeliveryException.class);
            assertThatThrownBy(() -> dispatcher.execute(() -> {
            }))
                    .isInstanceOf(MessageDeliveryException.class);
            channel.send(message("a", 2));
            await(handled);
        }
    }

    @Test
    void preservesNestedSubmissionsAndAllSubscribers() throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        peer(sockets, "a");
        CountDownLatch done = new CountDownLatch(4);
        List<Integer> handled = new CopyOnWriteArrayList<>();
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher(
                "test",
                new WebRealtimeProperties.Stomp(),
                sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.addInterceptor(new ChannelInterceptor() {
                @Override
                public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel target) {
                    if (((byte[]) message.getPayload())[0] == 1) {
                        target.send(message("a", 2));
                    }
                    return message;
                }
            });
            channel.subscribe(message -> {
                handled.add((int) ((byte[]) message.getPayload())[0]);
                done.countDown();
            });
            channel.subscribe(message -> {
                handled.add((int) ((byte[]) message.getPayload())[0] * 10);
                done.countDown();
            });
            channel.send(message("a", 1));
            await(done);
            assertThat(handled).containsExactly(1, 10, 2, 20);
            assertThatThrownBy(() -> dispatcher.execute(() -> {
            }))
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Test
    void disconnectCleanupRunsEvenWhenCapacityIsExhausted() throws Exception {
        WebSocketSessions sockets = new WebSocketSessions();
        peer(sockets, "a");
        WebRealtimeProperties.Stomp limits = new WebRealtimeProperties.Stomp();
        limits.setChannelQueueCapacity(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        AtomicBoolean cleaned = new AtomicBoolean();
        try (StompChannelDispatcher dispatcher = new StompChannelDispatcher("test", limits, sockets)) {
            ExecutorSubscribableChannel channel = channel(dispatcher);
            channel.subscribe(message -> {
                if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.DISCONNECT) {
                    cleaned.set(true);
                } else {
                    entered.countDown();
                    await(unblock);
                }
            });
            try {
                channel.send(message("a", 1));
                await(entered);
                sockets.overloaded("a");
                SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
                headers.setSessionId("a");
                channel.send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
                assertThat(cleaned).isTrue();
            } finally {
                unblock.countDown();
            }
        }
    }

    /** 创建挂接了实际连接登记和断连清理的可控 WebSocket。 */
    private static AtomicReference<CloseStatus> peer(WebSocketSessions sockets, String id) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        AtomicBoolean open = new AtomicBoolean(true);
        AtomicReference<CloseStatus> closed = new AtomicReference<>();
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(session.isOpen()).thenAnswer(ignored -> open.get());
        WebSocketHandler transport = sockets.decorate(new TextWebSocketHandler());
        doAnswer(call -> {
            CloseStatus status = call.getArgument(0);
            closed.set(status);
            if (open.getAndSet(false)) {
                transport.afterConnectionClosed(session, status);
            }
            return null;
        }).when(session).close(any(CloseStatus.class));
        transport.afterConnectionEstablished(session);
        return closed;
    }

    /** 创建同时使用分派器拦截和执行的真实 Spring 通道。 */
    private static ExecutorSubscribableChannel channel(StompChannelDispatcher dispatcher) {
        ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel(dispatcher);
        channel.addInterceptor(dispatcher);
        return channel;
    }

    /** 创建指定连接的一字节消息。 */
    private static Message<byte[]> message(String sessionId, int value) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[] {(byte) value}, headers.getMessageHeaders());
    }

    /** 读取测试消息的连接标识。 */
    private static String session(Message<?> message) {
        return SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
    }

    /** 通过完成信号协调并发，超时只作为测试死锁保护。 */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
