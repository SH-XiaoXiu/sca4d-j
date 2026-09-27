package com.sca4d.iot.emqx.config;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.core.spring.config.CoreRedisAutoConfiguration;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.Sca4djRedis;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.connection.DeviceConnectionAdmission;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceConnectionResolver;
import com.sca4d.iot.api.connection.DeviceConnectionTerminator;
import com.sca4d.iot.api.message.DeviceTransport;
import com.sca4d.iot.api.protocol.DeviceUplinkDispatcher;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationService;
import com.sca4d.iot.emqx.authentication.EmqxDeviceConnectionRequestFactory;
import com.sca4d.iot.emqx.authentication.MqttAuthorizationPolicy;
import com.sca4d.iot.emqx.authentication.MqttAuthorizationService;
import com.sca4d.iot.emqx.connection.EmqxConnectionReconciler;
import com.sca4d.iot.emqx.connection.EmqxConnectionRepository;
import com.sca4d.iot.emqx.management.EmqxDeviceConnectionTerminator;
import com.sca4d.iot.emqx.management.EmqxManagementClient;
import com.sca4d.iot.emqx.management.HttpEmqxManagementClient;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import com.sca4d.iot.emqx.protocol.MqttProtocolCatalog;
import com.sca4d.iot.emqx.transport.EmqxMqttDeviceTransport;
import com.sca4d.iot.emqx.transport.EmqxMqttRuntime;
import com.sca4d.iot.emqx.transport.EmqxMqttTransport;
import com.sca4d.iot.emqx.transport.MqttInboundFailureStore;
import com.sca4d.iot.emqx.transport.MqttMessageObserver;
import com.sca4d.iot.emqx.transport.MqttMessageProcessingObserver;
import com.sca4d.iot.emqx.transport.MqttMessageRoute;
import com.sca4d.iot.emqx.transport.MqttMessageRouter;
import com.sca4d.iot.emqx.transport.MqttTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;

/** 在具体应用启用后装配 EMQX MQTT、认证、连接快照对账和 Management API，不开放 HTTP 路由。 */
@AutoConfiguration(after = CoreRedisAutoConfiguration.class, afterName = "com.sca4d.iot.config.IotAutoConfiguration")
@EnableConfigurationProperties(EmqxProperties.class)
@ConditionalOnProperty(prefix = "sca4dj.iot.emqx", name = "enabled", havingValue = "true")
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // 必需 Bean 由 Core、IoT 和具体应用按启用条件提供。
public class EmqxAutoConfiguration {

    /** 创建 EMQX 自动配置。 */
    public EmqxAutoConfiguration() {
    }

    /** @return 具体应用安装的 MQTT 协议目录 */
    @Bean
    MqttProtocolCatalog mqttProtocolCatalog(ObjectProvider<MqttDeviceProtocol> protocols) {
        return new MqttProtocolCatalog(protocols.orderedStream().toList());
    }

    /** @return 生成应用策略或协议 MQTT ACL 及稳定指纹的服务 */
    @Bean
    MqttAuthorizationService mqttAuthorizationService(
            MqttProtocolCatalog protocols,
            ObjectProvider<MqttAuthorizationPolicy> authorizationPolicies) {
        return new MqttAuthorizationService(
                protocols,
                authorizationPolicies.orderedStream().toList());
    }

    /** @return 生成 EMQX allow/deny、client_attrs、ACL 和 expire_at 的认证服务 */
    @Bean
    EmqxAuthenticationService emqxAuthenticationService(
            EmqxDeviceConnectionRequestFactory requests,
            DeviceConnectionAdmission admission,
            MqttAuthorizationService authorization) {
        return new EmqxAuthenticationService(requests, admission, authorization);
    }

    /** @return 标准 MQTT DeviceProtocol 路由和应用 MQTT 路由组成的唯一路由表 */
    @Bean
    MqttMessageRouter mqttMessageRouter(
            MqttProtocolCatalog protocols,
            DeviceConnectionResolver devices,
            DeviceUplinkDispatcher uplinks,
            ObjectProvider<MqttMessageRoute> applicationRoutes) {
        return new MqttMessageRouter(
                protocols,
                devices,
                uplinks,
                applicationRoutes.orderedStream().toList());
    }

    /** @return 当前实例唯一 MQTT 客户端和共享订阅运行时 */
    @Bean
    @ConditionalOnCapability(IotCapabilities.TRANSPORT)
    EmqxMqttRuntime emqxMqttRuntime(
            EmqxProperties properties,
            MqttMessageRouter router,
            Clock clock,
            ObjectProvider<MqttMessageObserver> observers,
            ObjectProvider<MqttMessageProcessingObserver> processingObservers,
            ObjectProvider<MqttInboundFailureStore> failureStore,
            EmqxConnectionReconciler connectionReconciler) {
        properties.initializeClientId();
        return new EmqxMqttRuntime(
                properties,
                router,
                clock,
                observers.orderedStream().toList(),
                processingObservers.orderedStream().toList(),
                failureStore.getIfAvailable(),
                connectionReconciler);
    }

    /** @return 路由和设备下行使用的 MQTT QoS 1 发布入口 */
    @Bean
    @ConditionalOnMissingBean(MqttTransport.class)
    MqttTransport mqttTransport(ObjectProvider<EmqxMqttRuntime> runtimes) {
        return new EmqxMqttTransport(runtimes);
    }

    /** @return 使用 MQTT QoS 1、Retain=false 的下行 Publisher */
    @Bean
    @ConditionalOnMissingBean(EmqxMqttDeviceTransport.class)
    DeviceTransport<MqttDeviceProtocol> emqxMqttDeviceTransport(MqttTransport publisher) {
        return new EmqxMqttDeviceTransport(publisher);
    }

    /** @return EMQX `5.8.9` Management API HTTP 客户端 */
    @Bean
    @ConditionalOnMissingBean(EmqxManagementClient.class)
    EmqxManagementClient emqxManagementClient(
            EmqxProperties properties) {
        properties.initializeClientId();
        return new HttpEmqxManagementClient(properties);
    }

    /** @return EMQX Client ID、Broker 节点和连接代次的专用 Redis 投影 */
    @Bean
    EmqxConnectionRepository emqxConnectionRepository(
            @Sca4djRedis RedisTemplate<Key, String> redis,
            RedisBatches redisBatches) {
        return new EmqxConnectionRepository(redis, redisBatches);
    }

    /** @return Session 可靠任务使用的 Broker 断连入口 */
    @Bean
    @ConditionalOnMissingBean(EmqxDeviceConnectionTerminator.class)
    DeviceConnectionTerminator emqxDeviceConnectionTerminator(
            EmqxManagementClient client,
            EmqxConnectionRepository connections) {
        return new EmqxDeviceConnectionTerminator(client, connections);
    }

    /** @return 使用 Redis 租约执行的 EMQX 当前连接对账服务 */
    @Bean
    EmqxConnectionReconciler emqxConnectionReconciler(
            EmqxManagementClient management,
            DeviceConnectionResolver devices,
            DeviceConnectionReceiver connections,
            EmqxConnectionRepository emqxConnections,
            MqttAuthorizationService authorization,
            DistributedLock distributedLock,
            Clock clock,
            ObjectProvider<ConnectionRegistry> commonConnections) {
        return new EmqxConnectionReconciler(
                management,
                devices,
                connections,
                emqxConnections,
                authorization,
                distributedLock,
                clock,
                commonConnections.getIfAvailable());
    }

    /** @return 按应用配置间隔修正 EMQX 与 Redis Connection 投影的集群计划 */
    @Bean
    ClusterTaskSchedule emqxConnectionReconciliationSchedule(
            EmqxConnectionReconciler reconciler,
            EmqxProperties properties) {
        return new ClusterTaskSchedule(
                new ScheduleKey("IOT/EMQX/CONNECTION_RECONCILIATION"),
                1,
                reconciler,
                new IntervalSchedule(
                        properties.getReconciliationInterval(),
                        properties.getReconciliationInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }
}
