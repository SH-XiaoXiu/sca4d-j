package com.sca4d.iot.emqx.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.retry.RetryExecutor;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.Sca4djRedis;
import com.sca4d.iot.api.connection.DeviceConnectionAdmission;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceConnectionResolver;
import com.sca4d.iot.api.protocol.DeviceUplinkDispatcher;
import com.sca4d.iot.emqx.authentication.EmqxDeviceConnectionRequestFactory;
import com.sca4d.iot.emqx.config.EmqxAutoConfiguration;
import com.sca4d.iot.emqx.config.EmqxProperties;
import com.sca4d.iot.emqx.connection.EmqxConnectionReconciler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.LifecycleProcessor;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** 通过 MQTT 客户端公开监听器和订阅回调验证观察顺序、失败隔离及 Spring 可选装配，不访问 Broker。 */
class EmqxMqttRuntimeTest {

    private static final String PREFIX = "$share/sca4dj-iot/$SYS/brokers/+/clients/+/";

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T00:00:00Z");

    private static final Clock CLOCK = Clock.fixed(OBSERVED_AT, ZoneOffset.UTC);

    @Test
    @DisplayName("多个观察器按快照顺序通知，异常不阻止其他观察器、投影刷新和确认")
    void isolatesObserversBeforeProjectionAndAcknowledgement() {
        List<String> actions = new ArrayList<>();
        List<MqttConnectionEvent> events = new ArrayList<>();
        List<MqttConnectionEventObserver> observers = new ArrayList<>();
        observers.add(event -> {
            actions.add("first");
            event.payload()[0] = 0;
            throw new IllegalStateException("observer failure");
        });
        observers.add(event -> {
            actions.add("second");
            events.add(event);
        });
        EmqxConnectionReconciler reconciler = mock(EmqxConnectionReconciler.class);
        doAnswer(invocation -> {
            actions.add("refresh:" + invocation.getArgument(0));
            return null;
        }).when(reconciler).refreshClient(any());

        try (MqttClientBoundary mqtt = new MqttClientBoundary()) {
            mqtt.runtime(observers, reconciler);
            observers.clear();
            mqtt.connected();
            mqtt.deliver("connected", "{\"clientid\":\"client\",\"reason\":\"normal\"}", actions);

            assertThat(actions).containsExactly("first", "second", "refresh:client", "ack");
            MqttConnectionEvent event = events.getFirst();
            assertThat(event.clientId()).isEqualTo("client");
            assertThat(event.topic()).isEqualTo("$SYS/brokers/node/clients/client/connected");
            assertThat(event.connected()).isTrue();
            assertThat(event.observedAt()).isEqualTo(OBSERVED_AT);
            assertThat(new String(event.payload(), StandardCharsets.UTF_8)).contains("normal");
        }
    }

    @Test
    @DisplayName("没有观察器仍刷新和确认，刷新或解析失败保留原有确认语义")
    void keepsAcknowledgementSemanticsWithoutObserversAndAfterFailures() {
        List<String> actions = new ArrayList<>();
        EmqxConnectionReconciler reconciler = mock(EmqxConnectionReconciler.class);
        doAnswer(invocation -> {
            actions.add("refresh");
            throw new IllegalStateException("management unavailable");
        }).when(reconciler).refreshClient(any());
        try (MqttClientBoundary mqtt = new MqttClientBoundary()) {
            mqtt.runtime(List.of(), reconciler);
            mqtt.connected();
            mqtt.deliver("disconnected", "{\"clientid\":\"client\"}", actions);
            mqtt.deliver("connected", "{}", actions);
            assertThat(actions).containsExactly("refresh", "ack", "ack");
        }
    }

    @Test
    @DisplayName("刷新失败前仍收到断开事件，并保留确认语义")
    void observesDisconnectBeforeRefreshFailure() {
        List<String> actions = new ArrayList<>();
        List<MqttConnectionEvent> events = new ArrayList<>();
        EmqxConnectionReconciler reconciler = mock(EmqxConnectionReconciler.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("management unavailable");
        }).when(reconciler).refreshClient(any());
        try (MqttClientBoundary mqtt = new MqttClientBoundary()) {
            mqtt.runtime(List.of(events::add), reconciler);
            mqtt.connected();
            mqtt.deliver("disconnected", "{\"clientid\":\"client\"}", actions);
            assertThat(events).hasSize(1);
            assertThat(events.getFirst().connected()).isFalse();
            assertThat(actions).containsExactly("ack");
        }
    }

    @Test
    @DisplayName("真实自动配置无需连接观察器 Bean，并按 Ordered 顺序通知多个 Bean")
    void autoConfigurationSupportsOptionalOrderedObservers() {
        List<String> actions = new ArrayList<>();
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EmqxAutoConfiguration.class))
                .withPropertyValues(
                        "sca4dj.iot.emqx.enabled=true",
                        "sca4dj.iot.emqx.client-id=test-runtime",
                        "sca4dj.iot.emqx.management-base-url=http://localhost:18083",
                        "sca4dj.iot.emqx.management-api-key=test-key",
                        "sca4dj.iot.emqx.management-secret=test-secret")
                .withInitializer(
                        context -> context.getBeanFactory()
                                .registerSingleton(
                                        "lifecycleProcessor",
                                        mock(LifecycleProcessor.class)))
                .withBean(Clock.class, () -> CLOCK)
                .withBean(RetryExecutor.class, () -> mock(RetryExecutor.class))
                .withBean(DistributedLock.class, () -> mock(DistributedLock.class))
                .withBean(DeviceConnectionAdmission.class, () -> mock(DeviceConnectionAdmission.class))
                .withBean(DeviceConnectionResolver.class, () -> mock(DeviceConnectionResolver.class))
                .withBean(DeviceConnectionReceiver.class, () -> mock(DeviceConnectionReceiver.class))
                .withBean(DeviceUplinkDispatcher.class, () -> mock(DeviceUplinkDispatcher.class))
                .withBean(
                        EmqxDeviceConnectionRequestFactory.class,
                        () -> mock(EmqxDeviceConnectionRequestFactory.class))
                .withBean(RedisBatches.class, () -> mock(RedisBatches.class))
                .withBean(
                        "redis",
                        RedisTemplate.class,
                        () -> mock(RedisTemplate.class),
                        definition -> ((AbstractBeanDefinition) definition)
                                .addQualifier(new AutowireCandidateQualifier(Sca4djRedis.class)));
        try (MqttClientBoundary mqtt = new MqttClientBoundary()) {
            runner.run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(EmqxMqttRuntime.class);
                mqtt.connected();
                mqtt.deliver("connected", "{\"clientid\":\"client\"}", actions);
                assertThat(actions).containsExactly("ack");
            });
        }
        actions.clear();
        try (MqttClientBoundary mqtt = new MqttClientBoundary()) {
            runner.withBean("later", MqttConnectionEventObserver.class, () -> new OrderedObserver(20, "later", actions))
                    .withBean(
                            "earlier",
                            MqttConnectionEventObserver.class,
                            () -> new OrderedObserver(10, "earlier", actions))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        mqtt.connected();
                        mqtt.deliver("disconnected", "{\"clientid\":\"client\"}", actions);
                        assertThat(actions).containsExactly("earlier", "later", "ack");
                    });
        }
    }

    /** 只替代 MQTT 网络边界，保留 Runtime 的真实订阅注册和事件回调。 */
    private static final class MqttClientBoundary implements AutoCloseable {
        private final MockedStatic<Mqtt5Client> factory = mockStatic(Mqtt5Client.class);

        private final Map<String, Consumer<Mqtt5Publish>> subscriptions = new LinkedHashMap<>();

        private MqttClientConnectedListener listener;

        /** 捕获客户端公开连接监听器及实际安装的订阅。 */
        private MqttClientBoundary() {
            Mqtt5ClientBuilder builder = mock(Mqtt5ClientBuilder.class, RETURNS_SELF);
            Mqtt5AsyncClient client = mock(Mqtt5AsyncClient.class);
            factory.when(Mqtt5Client::builder).thenReturn(builder);
            when(builder.buildAsync()).thenReturn(client);
            doAnswer(invocation -> {
                listener = invocation.getArgument(0);
                return builder;
            }).when(builder).addConnectedListener(any());
            Mqtt5SubAck subAck = mock(Mqtt5SubAck.class);
            when(subAck.getReasonCodes()).thenReturn(List.of(Mqtt5SubAckReasonCode.GRANTED_QOS_1));
            when(client.subscribe(any(Mqtt5Subscribe.class), any(), anyBoolean())).thenAnswer(invocation -> {
                Mqtt5Subscribe subscription = invocation.getArgument(0);
                subscriptions.put(
                        subscription.getSubscriptions().getFirst().getTopicFilter().toString(),
                        invocation.getArgument(1));
                return CompletableFuture.completedFuture(subAck);
            });
        }

        /** 手动构造 Runtime，验证不依赖 Spring 的观察器集合契约。 */
        private void runtime(List<MqttConnectionEventObserver> observers, EmqxConnectionReconciler reconciler) {
            EmqxProperties properties = new EmqxProperties();
            properties.setClientId("test-runtime");
            MqttMessageRouter router = mock(MqttMessageRouter.class);
            when(router.topicFilters()).thenReturn(List.of());
            new EmqxMqttRuntime(
                    properties,
                    router,
                    CLOCK,
                    List.of(),
                    List.of(),
                    observers,
                    null,
                    reconciler,
                    mock(RetryExecutor.class));
        }

        /** 触发客户端公开连接事件，Runtime 将安装真实的系统事件订阅。 */
        private void connected() {
            listener.onConnected(mock(MqttClientConnectedContext.class));
        }

        /** 向 Runtime 实际登记的订阅回调交付事件，并记录确认顺序。 */
        private void deliver(String suffix, String payload, List<String> actions) {
            Mqtt5Publish publish = mock(Mqtt5Publish.class);
            when(publish.getTopic()).thenReturn(
                    MqttTopic.of(
                            "$SYS/brokers/node/clients/client/" + suffix));
            when(publish.getPayloadAsBytes()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
            doAnswer(invocation -> {
                actions.add("ack");
                return null;
            }).when(publish).acknowledge();
            subscriptions.get(PREFIX + suffix).accept(publish);
        }

        @Override
        public void close() {
            factory.close();
        }
    }

    /** 用可观察结果核对 Spring Ordered Bean 的注入顺序。 */
    private static final class OrderedObserver implements MqttConnectionEventObserver, Ordered {
        private final List<String> actions;

        private final String name;

        private final int order;

        private OrderedObserver(int order, String name, List<String> actions) {
            this.order = order;
            this.name = name;
            this.actions = actions;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void connectionChanged(MqttConnectionEvent event) {
            actions.add(name);
        }
    }
}
