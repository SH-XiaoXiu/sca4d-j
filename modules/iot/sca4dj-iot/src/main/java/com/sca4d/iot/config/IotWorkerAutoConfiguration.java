package com.sca4d.iot.config;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.messaging.JacksonMessageCodec;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.connection.DeviceConnectionTerminator;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceAcknowledgementCheck;
import com.sca4d.iot.api.message.DeviceDispatchMessage;
import com.sca4d.iot.api.message.DeviceTransport;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.connection.DeviceConnectionRepository;
import com.sca4d.iot.connection.DeviceConnectionTerminatorRouter;
import com.sca4d.iot.connection.DeviceDisconnectHandler;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DeviceAcknowledgementCheckHandler;
import com.sca4d.iot.message.DeviceAcknowledgementHandler;
import com.sca4d.iot.message.DeviceAcknowledgementService;
import com.sca4d.iot.message.DeviceDeliveryScheduler;
import com.sca4d.iot.message.DeviceDispatchHandler;
import com.sca4d.iot.message.DeviceTransportRouter;
import com.sca4d.iot.message.DownlinkRepository;
import com.sca4d.iot.message.DownlinkRetentionTask;
import com.sca4d.iot.protocol.DeviceProtocolCatalog;
import com.sca4d.iot.schedule.DefaultDeviceScheduleService;
import com.sca4d.iot.schedule.DeviceScheduleTask;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** 在具体应用选择 Transport 或 Connection 终止入口后创建 IoT 可靠任务 Worker。 */
@AutoConfiguration(after = IotAutoConfiguration.class, afterName = "com.sca4d.iot.emqx.config.EmqxAutoConfiguration")
public class IotWorkerAutoConfiguration {

    /** @return 不依赖 ACK Consumer 实例的消息编码器 */
    @Bean
    @ConditionalOnMissingBean(name = "deviceAcknowledgementCodec")
    MessageCodec<DeviceAcknowledgement> deviceAcknowledgementCodec(@Sca4djJson Sca4djMapper json) {
        return new JacksonMessageCodec<>(
                DeviceAcknowledgement.CODE,
                DeviceAcknowledgement.PROTOCOL_VERSION,
                DeviceAcknowledgement.class,
                json);
    }

    /** @return 不依赖下行 Consumer 实例的消息编码器 */
    @Bean
    @ConditionalOnMissingBean(name = "deviceDispatchCodec")
    MessageCodec<DeviceDispatchMessage> deviceDispatchCodec(@Sca4djJson Sca4djMapper json) {
        return new JacksonMessageCodec<>(
                DeviceDispatchMessage.CODE,
                DeviceDispatchMessage.PROTOCOL_VERSION,
                DeviceDispatchMessage.class,
                json);
    }

    /** @return 不依赖 ACK 超时 Consumer 实例的消息编码器 */
    @Bean
    @ConditionalOnMissingBean(name = "deviceAcknowledgementCheckCodec")
    MessageCodec<DeviceAcknowledgementCheck> deviceAcknowledgementCheckCodec(@Sca4djJson Sca4djMapper json) {
        return new JacksonMessageCodec<>(
                DeviceAcknowledgementCheck.CODE,
                DeviceAcknowledgementCheck.PROTOCOL_VERSION,
                DeviceAcknowledgementCheck.class,
                json);
    }

    /** @return 不依赖断连 Consumer 实例的消息编码器 */
    @Bean
    @ConditionalOnMissingBean(name = "deviceDisconnectCodec")
    MessageCodec<DeviceDisconnectMessage> deviceDisconnectCodec(@Sca4djJson Sca4djMapper json) {
        return new JacksonMessageCodec<>(
                DeviceDisconnectMessage.CODE,
                DeviceDisconnectMessage.PROTOCOL_VERSION,
                DeviceDisconnectMessage.class,
                json);
    }

    /** @return 在来源事务提交后处理可靠 ACK 请求的业务 Consumer */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnCapability(IotCapabilities.TRANSPORT)
    DeviceAcknowledgementHandler deviceAcknowledgementHandler(
            DeviceAcknowledgementService acknowledgements,
            MessageCodec<DeviceAcknowledgement> codec) {
        return new DeviceAcknowledgementHandler(acknowledgements, codec);
    }

    /** @return 按具体 `DeviceProtocol` Java 类型选择下行 Publisher 的路由 */
    @Bean
    @ConditionalOnBean(DeviceTransport.class)
    @ConditionalOnMissingBean
    DeviceTransportRouter deviceDownlinkRouter(
            DeviceRepository devices,
            DeviceProtocolCatalog protocols,
            ObjectProvider<DeviceTransport<?>> publishers) {
        return new DeviceTransportRouter(devices, protocols, publishers.orderedStream().toList());
    }

    /** @return 竞争消费可靠下行发送消息的业务 Consumer */
    @Bean
    @ConditionalOnBean(DeviceTransportRouter.class)
    @ConditionalOnMissingBean
    @ConditionalOnCapability(IotCapabilities.TRANSPORT)
    DeviceDispatchHandler deviceDispatchHandler(
            DownlinkRepository downlinks,
            DeviceTransportRouter router,
            DeviceDeliveryScheduler messages,
            Clock clock,
            DeviceChangeNotifier changes,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager,
            MessageCodec<DeviceDispatchMessage> codec) {
        return new DeviceDispatchHandler(
                downlinks,
                router,
                messages,
                clock,
                changes,
                distributedLock,
                new TransactionTemplate(transactionManager),
                codec);
    }

    /** @return 消费延迟消息并处理分阶段 ACK 超时的业务 Consumer */
    @Bean
    @ConditionalOnBean(DeviceTransportRouter.class)
    @ConditionalOnMissingBean
    @ConditionalOnCapability(IotCapabilities.TRANSPORT)
    DeviceAcknowledgementCheckHandler deviceAcknowledgementCheckHandler(
            DownlinkRepository downlinks,
            DeviceDeliveryScheduler messages,
            Clock clock,
            DeviceChangeNotifier changes,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager,
            MessageCodec<DeviceAcknowledgementCheck> codec) {
        return new DeviceAcknowledgementCheckHandler(
                downlinks,
                messages,
                clock,
                changes,
                distributedLock,
                new TransactionTemplate(transactionManager),
                codec);
    }

    /** @return 按 `DeviceConnectionProtocol` 选择连接终止实现的路由 */
    @Bean
    @ConditionalOnBean(DeviceConnectionTerminator.class)
    @ConditionalOnMissingBean
    DeviceConnectionTerminatorRouter deviceConnectionTerminatorRouter(
            ObjectProvider<DeviceConnectionTerminator> terminators) {
        return new DeviceConnectionTerminatorRouter(terminators.orderedStream().toList());
    }

    /** @return 竞争消费可靠断连消息的业务 Consumer */
    @Bean
    @ConditionalOnBean(DeviceConnectionTerminatorRouter.class)
    @ConditionalOnMissingBean
    @ConditionalOnCapability(IotCapabilities.TRANSPORT)
    DeviceDisconnectHandler deviceDisconnectHandler(
            DeviceConnectionTerminatorRouter terminators,
            DeviceConnectionRepository connections,
            Clock clock,
            MessageBus messages,
            ObjectProvider<ConnectionRegistry> commonConnections,
            MessageCodec<DeviceDisconnectMessage> codec) {
        return new DeviceDisconnectHandler(
                terminators,
                connections,
                messages,
                clock,
                commonConnections.getIfAvailable(),
                codec);
    }

    /** @return 分批删除保留期外终态下行消息的集群任务 */
    @Bean
    @ConditionalOnMissingBean
    DownlinkRetentionTask downlinkRetentionTask(
            DownlinkRepository downlinks,
            Clock clock,
            IotProperties properties) {
        properties.validate();
        return new DownlinkRetentionTask(
                downlinks,
                clock,
                properties.getDownlinkRetention(),
                properties.getDownlinkCleanupBatchSize());
    }

    /** @return 按应用配置间隔触发终态下行消息清理的集群计划 */
    @Bean
    ClusterTaskSchedule downlinkRetentionSchedule(
            DownlinkRetentionTask task,
            IotProperties properties) {
        return new ClusterTaskSchedule(
                new ScheduleKey("IOT/DOWNLINK/RETENTION"),
                1,
                task,
                new IntervalSchedule(
                        properties.getDownlinkCleanupInterval(),
                        properties.getDownlinkCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }

    /** @return 按毫秒到期索引批量生成可靠设备下行的集群任务 */
    @Bean
    @ConditionalOnMissingBean
    DeviceScheduleTask deviceScheduleTask(
            DefaultDeviceScheduleService schedules,
            IotProperties properties) {
        properties.validate();
        return new DeviceScheduleTask(
                schedules,
                properties.getDeviceScheduleBatchSize());
    }

    /** @return 按应用配置精度扫描到期设备规则的集群计划 */
    @Bean
    ClusterTaskSchedule deviceScheduleScanSchedule(
            DeviceScheduleTask task,
            IotProperties properties) {
        return new ClusterTaskSchedule(
                new ScheduleKey("IOT/DEVICE_SCHEDULE/SCAN"),
                1,
                task,
                new IntervalSchedule(properties.getScheduleTick(), properties.getScheduleTick()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }
}
