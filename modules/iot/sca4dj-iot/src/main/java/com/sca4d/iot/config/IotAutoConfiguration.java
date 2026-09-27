package com.sca4d.iot.config;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.RedisHashTag;
import com.sca4d.core.spring.redis.Sca4djRedis;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.change.DeviceChangePublisher;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionQueryService;
import com.sca4d.iot.api.connection.DeviceOnlinePolicy;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceUplinkReceiver;
import com.sca4d.iot.api.message.RetryPolicy;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.protocol.DeviceProtocol;
import com.sca4d.iot.api.protocol.DeviceUplinkDispatcher;
import com.sca4d.iot.api.query.DeviceHistoryQueryService;
import com.sca4d.iot.api.query.DeviceRuntimeQueryService;
import com.sca4d.iot.api.state.ReportedStateStore;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.connection.ConnectionPresentDeviceOnlinePolicy;
import com.sca4d.iot.connection.DeviceConnectionRepository;
import com.sca4d.iot.connection.DeviceConnectionService;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.device.DeviceService;
import com.sca4d.iot.message.BuiltInDevicePolicies;
import com.sca4d.iot.message.DesiredStateService;
import com.sca4d.iot.message.DeviceAcknowledgementService;
import com.sca4d.iot.message.DeviceCommandService;
import com.sca4d.iot.message.DeviceDeliveryScheduler;
import com.sca4d.iot.message.DevicePolicyCatalog;
import com.sca4d.iot.message.DownlinkRepository;
import com.sca4d.iot.message.StandardDeviceUplinkReceiver;
import com.sca4d.iot.model.CompositeThingModelRepository;
import com.sca4d.iot.model.StaticThingModelRepository;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.model.ThingModelI18nValidator;
import com.sca4d.iot.model.ThingModelJsonCodec;
import com.sca4d.iot.model.ThingModelService;
import com.sca4d.iot.model.ThingValueValidator;
import com.sca4d.iot.persistence.DatabaseThingModelRepository;
import com.sca4d.iot.persistence.mapper.DesiredPropertyMapper;
import com.sca4d.iot.persistence.mapper.DeviceDownlinkMapper;
import com.sca4d.iot.persistence.mapper.DeviceMapper;
import com.sca4d.iot.persistence.mapper.DeviceScheduleMapper;
import com.sca4d.iot.persistence.mapper.ProductMapper;
import com.sca4d.iot.persistence.mapper.ReportedStateMapper;
import com.sca4d.iot.persistence.mapper.ThingModelMapper;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import com.sca4d.iot.product.ProductRepository;
import com.sca4d.iot.product.ProductService;
import com.sca4d.iot.protocol.DeviceProtocolCatalog;
import com.sca4d.iot.protocol.StandardDeviceUplinkDispatcher;
import com.sca4d.iot.query.DefaultDeviceHistoryQueryService;
import com.sca4d.iot.query.DefaultDeviceRuntimeQueryService;
import com.sca4d.iot.schedule.DefaultDeviceScheduleService;
import com.sca4d.iot.schedule.DeviceScheduleRepository;
import com.sca4d.iot.state.PostgresqlReportedStateStore;
import com.sca4d.iot.state.RedisReportedStateStore;
import com.sca4d.iot.state.ReportedStateService;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

/** 自动装配不开放 HTTP 路由的 IoT 领域服务、持久化、Redis 投影和消息协作对象。 */
@AutoConfiguration
@MapperScan(basePackageClasses = ProductMapper.class)
@EnableConfigurationProperties(IotProperties.class)
public class IotAutoConfiguration {

    /** IoT 连接反向索引和 reported 投影由多 key Lua 原子修改。 */
    @Bean
    RedisHashTag iotRedisHashTag() {
        return new RedisHashTag(TreeCode.ofSegment("IOT"));
    }

    /** @return IoT 开放连接值类型的 MyBatis 字符串映射 */
    @Bean
    ConfigurationCustomizer iotTypeHandlerCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry()
                    .register(
                            DeviceConnectionId.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(DeviceConnectionId::new, DeviceConnectionId::value));
            configuration.getTypeHandlerRegistry()
                    .register(
                            DeviceConnectionProtocol.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(
                                    DeviceConnectionProtocol::new,
                                    DeviceConnectionProtocol::code));
        };
    }

    /** @return 事务提交后调用 App 可选发布器的 Device 失效通知入口 */
    @Bean
    DeviceChangeNotifier deviceChangeNotifier(ObjectProvider<DeviceChangePublisher> publishers) {
        return new DeviceChangeNotifier(publishers.orderedStream().toList());
    }

    /** @return Product 持久化入口 */
    @Bean
    ProductRepository productRepository(
            ProductMapper mapper,
            SqlBatches sqlBatches,
            IotProperties properties) {
        properties.validate();
        return new ProductRepository(mapper, sqlBatches, properties.getProductCatalogScanBatchSize());
    }

    /** @return 数据库 Product 与应用代码 Product 组成的统一定义目录 */
    @Bean
    ProductDefinitionCatalog productDefinitionCatalog(
            ProductRepository products,
            ObjectProvider<ProductDefinition> codeDefinitions) {
        return new ProductDefinitionCatalog(products, codeDefinitions.orderedStream().toList());
    }

    /** @return 数据库迁移完成后拒绝跨来源 Product code 冲突的启动检查 */
    @Bean
    SmartInitializingSingleton productDefinitionCollisionValidator(ProductDefinitionCatalog products) {
        return products::validateNoCollisions;
    }

    /** @return Device 持久化入口 */
    @Bean
    DeviceRepository deviceRepository(DeviceMapper mapper, SqlBatches sqlBatches) {
        return new DeviceRepository(mapper, sqlBatches);
    }

    /** @return desired state、标准下行消息和统一回执持久化入口 */
    @Bean
    DownlinkRepository downlinkRepository(
            DeviceDownlinkMapper messages,
            DesiredPropertyMapper desired,
            ThingDataJsonCodec json,
            DevicePolicyCatalog policies,
            SqlBatches sqlBatches) {
        return new DownlinkRepository(messages, desired, json, policies, sqlBatches);
    }

    /** @return 在业务事务中登记下行发送和 ACK 超时消息的发布器 */
    @Bean
    DeviceDeliveryScheduler deviceDeliveryScheduler(
            MessageBus messages) {
        return new DeviceDeliveryScheduler(messages);
    }

    /** @return 默认使用 PostgreSQL 保存 latest reported 投影 */
    @Bean
    @ConditionalOnMissingBean(ReportedStateStore.class)
    @ConditionalOnProperty(prefix = "sca4dj.iot", name = "reported-store", havingValue = "postgresql", matchIfMissing = true)
    ReportedStateStore postgresqlReportedStateStore(
            ReportedStateMapper mapper,
            ThingDataJsonCodec json,
            SqlBatches sqlBatches) {
        return new PostgresqlReportedStateStore(mapper, json, sqlBatches);
    }

    /** @return 具体应用选择 Redis 时使用 Lua 原子保存 latest reported 投影 */
    @Bean
    @ConditionalOnMissingBean(ReportedStateStore.class)
    @ConditionalOnProperty(prefix = "sca4dj.iot", name = "reported-store", havingValue = "redis")
    ReportedStateStore redisReportedStateStore(
            @Sca4djRedis RedisTemplate<Key, String> redis,
            ThingDataJsonCodec json,
            RedisBatches redisBatches) {
        return new RedisReportedStateStore(redis, json, redisBatches);
    }

    /** @return 设备定时规则和执行结果的主库持久化入口 */
    @Bean
    DeviceScheduleRepository deviceScheduleRepository(
            DeviceScheduleMapper mapper,
            ThingDataJsonCodec json) {
        return new DeviceScheduleRepository(mapper, json);
    }

    /** @param objectMapper App 统一 JSON 映射器 @return 严格物模型 JSON 编解码器 */
    @Bean
    ThingModelJsonCodec thingModelJsonCodec(
            @Sca4djJson Sca4djMapper objectMapper,
            DevicePolicyCatalog policies) {
        return new ThingModelJsonCodec(objectMapper, policies);
    }

    /** @param objectMapper App 统一 JSON 映射器 @return 标准 Thing data JSON 编解码器 */
    @Bean
    ThingDataJsonCodec thingDataJsonCodec(@Sca4djJson Sca4djMapper objectMapper) {
        return new ThingDataJsonCodec(objectMapper);
    }

    /** @return 递归校验物模型 Java 值的无状态校验器 */
    @Bean
    ThingValueValidator thingValueValidator() {
        return new ThingValueValidator();
    }

    /** @param protocols App 安装的协议实现 @return 唯一 code 协议目录 */
    @Bean
    DeviceProtocolCatalog deviceProtocolCatalog(ObjectProvider<DeviceProtocol> protocols) {
        return new DeviceProtocolCatalog(protocols.orderedStream().toList());
    }

    /** @return 内置策略与 App 扩展策略组成的唯一 code 目录 */
    @Bean
    DevicePolicyCatalog devicePolicyCatalog(
            ObjectProvider<AcknowledgementPolicy> acknowledgementPolicies,
            ObjectProvider<RetryPolicy> retryPolicies) {
        List<AcknowledgementPolicy> acknowledgements = Stream.concat(
                BuiltInDevicePolicies.acknowledgements().stream(),
                acknowledgementPolicies.orderedStream()).toList();
        List<RetryPolicy> retries = Stream.concat(
                BuiltInDevicePolicies.retries().stream(),
                retryPolicies.orderedStream()).toList();
        return new DevicePolicyCatalog(acknowledgements, retries);
    }

    /** @return 主 PostgreSQL 物模型仓储 */
    @Bean
    DatabaseThingModelRepository databaseThingModelRepository(
            ThingModelMapper mapper,
            ThingModelJsonCodec codec,
            Clock clock) {
        return new DatabaseThingModelRepository(mapper, codec, clock);
    }

    /** @return 应用提供的全部只读代码 ThingModel */
    @Bean
    StaticThingModelRepository staticThingModelRepository(
            ObjectProvider<ThingModel> models,
            ObjectProvider<ThingModelPresentation> presentations) {
        return new StaticThingModelRepository(
                models.orderedStream().toList(),
                presentations.orderedStream().toList());
    }

    /** @return 按 Product 来源选择代码或数据库的唯一 ThingModelRepository */
    @Bean
    @Primary
    ThingModelRepository thingModelRepository(
            ProductDefinitionCatalog products,
            StaticThingModelRepository codeModels,
            DatabaseThingModelRepository databaseModels) {
        return new CompositeThingModelRepository(products, codeModels, databaseModels);
    }

    /** @return Product 公共领域服务 */
    @Bean
    ProductService productService(
            ProductRepository products,
            ProductDefinitionCatalog definitions,
            DeviceRepository devices,
            DeviceProtocolCatalog protocols,
            ThingModelRepository models,
            IdGenerator ids,
            LocaleCatalog locales,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager) {
        return new ProductService(
                products,
                definitions,
                devices,
                protocols,
                models,
                ids,
                locales,
                distributedLock,
                new TransactionTemplate(transactionManager));
    }

    /** @return 按默认 Locale 和物模型节点校验动态翻译的校验器 */
    @Bean
    ThingModelI18nValidator thingModelI18nValidator(LocaleCatalog locales) {
        return new ThingModelI18nValidator(locales);
    }

    /** @return 物模型发布和查询服务 */
    @Bean
    ThingModelService thingModelService(
            ThingModelRepository models,
            ProductRepository products,
            DevicePolicyCatalog policies,
            DeviceProtocolCatalog protocols,
            ThingModelJsonCodec codec,
            ThingModelI18nValidator i18nValidator,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager) {
        return new ThingModelService(
                models,
                products,
                policies,
                protocols,
                codec,
                i18nValidator,
                distributedLock,
                new TransactionTemplate(transactionManager));
    }

    /** @return 连接信息的 Redis Repository 和设备接入事件接收入口 */
    @Bean
    DeviceConnectionRepository deviceConnectionRepository(
            @Sca4djRedis RedisTemplate<Key, String> redis,
            Clock clock,
            IotProperties properties,
            DeviceChangeNotifier changes,
            RedisBatches redisBatches) {
        properties.validate();
        return new DeviceConnectionRepository(
                redis,
                clock,
                properties.getConnectionTtl(),
                changes,
                redisBatches);
    }

    /** @return 连接投影非空时判定在线的默认策略 */
    @Bean
    @ConditionalOnMissingBean(DeviceOnlinePolicy.class)
    DeviceOnlinePolicy deviceOnlinePolicy() {
        return new ConnectionPresentDeviceOnlinePolicy();
    }

    /** @return 为 Device 当前连接登记可靠断连消息的公共入口 */
    @Bean
    DeviceConnectionService deviceConnectionService(
            DeviceConnectionQueryService connections,
            MessageBus messages,
            Clock clock,
            ObjectProvider<ConnectionRegistry> commonConnections) {
        return new DeviceConnectionService(
                connections,
                messages,
                clock,
                commonConnections.getIfAvailable());
    }

    /** @return Device 创建和绑定修改服务 */
    @Bean
    DeviceService deviceService(
            DeviceRepository devices,
            ProductDefinitionCatalog products,
            DownlinkRepository downlinks,
            ReportedStateStore reportedStates,
            ThingModelRepository models,
            DeviceProtocolCatalog protocols,
            Clock clock,
            DeviceChangeNotifier changes,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager) {
        return new DeviceService(
                devices,
                products,
                downlinks,
                reportedStates,
                models,
                protocols,
                clock,
                changes,
                distributedLock,
                new TransactionTemplate(transactionManager));
    }

    /** @return 原子修改 desired 并创建可靠状态同步消息的服务 */
    @Bean
    DesiredStateService desiredStateService(
            DeviceRepository devices,
            ProductDefinitionCatalog products,
            DownlinkRepository downlinks,
            ThingModelRepository models,
            ThingValueValidator validator,
            IdGenerator ids,
            Clock clock,
            DeviceChangeNotifier changes,
            ThingDataJsonCodec json,
            DeviceDeliveryScheduler messagePublisher,
            IotProperties properties) {
        properties.validate();
        return new DesiredStateService(
                devices,
                products,
                downlinks,
                models,
                validator,
                ids,
                clock,
                changes,
                json,
                messagePublisher,
                properties.getMaxDesiredProperties());
    }

    /** @return 校验 command 并创建可靠下行消息的服务 */
    @Bean
    DeviceCommandService deviceCommandService(
            DeviceRepository devices,
            ProductDefinitionCatalog products,
            DownlinkRepository downlinks,
            ThingModelRepository models,
            ThingValueValidator validator,
            IdGenerator ids,
            Clock clock,
            DeviceChangeNotifier changes,
            DeviceDeliveryScheduler messagePublisher) {
        return new DeviceCommandService(
                devices,
                products,
                downlinks,
                models,
                validator,
                ids,
                clock,
                changes,
                messagePublisher);
    }

    /** @return 显式 ACK 与 reported 推导 ACK 共用的状态转换服务 */
    @Bean
    DeviceAcknowledgementService deviceAcknowledgementService(
            DownlinkRepository downlinks,
            Clock clock,
            DeviceChangeNotifier changes,
            DeviceDeliveryScheduler downlinkMessages,
            MessageBus messages,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager) {
        return new DeviceAcknowledgementService(
                downlinks,
                clock,
                changes,
                downlinkMessages,
                messages,
                distributedLock,
                new TransactionTemplate(transactionManager));
    }

    /** @return 设备定时管理、查询和到期下行生成服务 */
    @Bean
    DefaultDeviceScheduleService defaultDeviceScheduleService(
            DeviceScheduleRepository schedules,
            DeviceRepository devices,
            DeviceCommandService commands,
            IdGenerator ids,
            Clock clock,
            DeviceChangeNotifier changes,
            IotProperties properties,
            MessageBus messages,
            ObjectProvider<DeviceHistoryStore> history,
            DistributedLock distributedLock,
            PlatformTransactionManager transactionManager) {
        return new DefaultDeviceScheduleService(
                schedules,
                devices,
                commands,
                ids,
                clock,
                changes,
                properties,
                messages,
                history,
                distributedLock,
                new TransactionTemplate(transactionManager));
    }

    /** @return 读取 Redis 连接与主库 reported、desired 当前投影的服务 */
    @Bean
    DeviceRuntimeQueryService deviceRuntimeQueryService(
            DeviceRepository devices,
            DeviceConnectionQueryService connections,
            ReportedStateStore reported,
            DownlinkRepository downlinks) {
        return new DefaultDeviceRuntimeQueryService(devices, connections, reported, downlinks);
    }

    /** @return 读取主库可靠下行历史和可选 Device 历史存储的服务 */
    @Bean
    DeviceHistoryQueryService deviceHistoryQueryService(
            DeviceRepository devices,
            DownlinkRepository downlinks,
            ObjectProvider<DeviceHistoryStore> history) {
        return new DefaultDeviceHistoryQueryService(devices, downlinks, history);
    }

    /** @return latest reported、收敛 ACK 和 IoT 消息发布的主事务服务 */
    @Bean
    ReportedStateService reportedStateService(
            DeviceRepository devices,
            ProductDefinitionCatalog products,
            ReportedStateStore reportedStates,
            DownlinkRepository downlinks,
            ThingModelRepository models,
            ThingValueValidator validator,
            IdGenerator ids,
            MessageBus messages,
            Clock clock,
            DeviceChangeNotifier changes,
            IotProperties properties) {
        properties.validate();
        return new ReportedStateService(
                devices,
                products,
                reportedStates,
                downlinks,
                models,
                validator,
                ids,
                messages,
                clock,
                changes,
                properties.getMaxReportedProperties(),
                properties.getMaxEventsPerMessage());
    }

    /** @return 设备接入实现提交标准 reported、事件和回执的公共入口 */
    @Bean
    @ConditionalOnMissingBean(DeviceUplinkReceiver.class)
    DeviceUplinkReceiver deviceUplinkReceiver(
            ReportedStateService reportedStates,
            DeviceAcknowledgementService acknowledgements) {
        return new StandardDeviceUplinkReceiver(reportedStates, acknowledgements);
    }

    /** @return 按 Device 当前协议解码并提交标准上行结果的传输无关分发器 */
    @Bean
    DeviceUplinkDispatcher deviceUplinkDispatcher(
            DeviceProtocolCatalog protocols,
            DeviceUplinkReceiver receiver) {
        return new StandardDeviceUplinkDispatcher(protocols, receiver);
    }
}
