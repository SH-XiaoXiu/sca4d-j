package com.sca4d.iot.config;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.iot.api.connection.DeviceConnectionTerminator;
import com.sca4d.iot.api.message.DeviceDownlinkPublisher;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.connection.DeviceConnectionRepository;
import com.sca4d.iot.connection.DeviceConnectionTerminatorRouter;
import com.sca4d.iot.connection.DeviceDisconnectConsumer;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DeviceAcknowledgementConsumer;
import com.sca4d.iot.message.DeviceAcknowledgementService;
import com.sca4d.iot.message.DeviceAcknowledgementTimeoutConsumer;
import com.sca4d.iot.message.DeviceDownlinkDispatchConsumer;
import com.sca4d.iot.message.DeviceDownlinkMessagePublisher;
import com.sca4d.iot.message.DeviceDownlinkRouter;
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

    /** @return 在来源事务提交后处理可靠 ACK 请求的业务 Consumer */
    @Bean
    @ConditionalOnMissingBean
    DeviceAcknowledgementConsumer deviceAcknowledgementConsumer(
            DeviceAcknowledgementService acknowledgements) {
        return new DeviceAcknowledgementConsumer(acknowledgements);
    }

    /** @return 按具体 `DeviceProtocol` Java 类型选择下行 Publisher 的路由 */
    @Bean
    @ConditionalOnBean(DeviceDownlinkPublisher.class)
    @ConditionalOnMissingBean
    DeviceDownlinkRouter deviceDownlinkRouter(
            DeviceRepository devices,
            DeviceProtocolCatalog protocols,
            ObjectProvider<DeviceDownlinkPublisher<?>> publishers) {
        return new DeviceDownlinkRouter(devices, protocols, publishers.orderedStream().toList());
    }

    /** @return 竞争消费可靠下行发送消息的业务 Consumer */
    @Bean
    @ConditionalOnBean(DeviceDownlinkRouter.class)
    @ConditionalOnMissingBean
    DeviceDownlinkDispatchConsumer deviceDownlinkDispatchConsumer(
            DownlinkRepository downlinks,
            DeviceDownlinkRouter router,
            DeviceDownlinkMessagePublisher messages,
            Clock clock,
            DeviceChangeNotifier changes,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager) {
        return new DeviceDownlinkDispatchConsumer(
                downlinks,
                router,
                messages,
                clock,
                changes,
                distributedLock,
                new TransactionTemplate(transactionManager));
    }

    /** @return 消费延迟消息并处理分阶段 ACK 超时的业务 Consumer */
    @Bean
    @ConditionalOnBean(DeviceDownlinkRouter.class)
    @ConditionalOnMissingBean
    DeviceAcknowledgementTimeoutConsumer deviceAcknowledgementTimeoutConsumer(
            DownlinkRepository downlinks,
            DeviceDownlinkMessagePublisher messages,
            Clock clock,
            DeviceChangeNotifier changes,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager) {
        return new DeviceAcknowledgementTimeoutConsumer(
                downlinks,
                messages,
                clock,
                changes,
                distributedLock,
                new TransactionTemplate(transactionManager));
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
    DeviceDisconnectConsumer deviceDisconnectConsumer(
            DeviceConnectionTerminatorRouter terminators,
            DeviceConnectionRepository connections,
            Clock clock,
            MessagePublisher messages,
            ObjectProvider<ConnectionRegistry> commonConnections) {
        return new DeviceDisconnectConsumer(
                terminators,
                connections,
                messages,
                clock,
                commonConnections.getIfAvailable());
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
                new ScheduleKey("iot.downlink.retention"),
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
                new ScheduleKey("iot.device-schedule.scan"),
                1,
                task,
                new IntervalSchedule(properties.getScheduleTick(), properties.getScheduleTick()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }
}
