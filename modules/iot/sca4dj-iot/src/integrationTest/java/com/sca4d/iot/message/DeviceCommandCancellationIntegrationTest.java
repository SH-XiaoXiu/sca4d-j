package com.sca4d.iot.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.JacksonMessageCodec;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.iot.api.message.DeviceDispatchMessage;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.policy.DefaultCommandRetryPolicy;
import com.sca4d.iot.message.policy.DeviceResultAcknowledgementPolicy;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.model.ThingValueValidator;
import com.sca4d.iot.persistence.mapper.DesiredPropertyMapper;
import com.sca4d.iot.persistence.mapper.DeviceDownlinkMapper;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 使用隔离 PostgreSQL 和真实 Mapper 验证取消提交、回滚及发布期间的竞争。 */
class DeviceCommandCancellationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:17-alpine");

    private static DriverManagerDataSource dataSource;

    private DownlinkRepository repository;

    private DeviceCommandService commands;

    private TransactionTemplate transactions;

    /** 实际 SqlSession 由 Spring 按事务关闭，SqlSessionTemplate 本身不允许调用 close。 */
    private SqlSessionTemplate sessions;

    private final DeviceDeliveryScheduler publisher = mock(DeviceDeliveryScheduler.class);

    private final DeviceChangeNotifier changes = new DeviceChangeNotifier(List.of());

    /** 从模块真实 schema 创建隔离数据库。 */
    @BeforeAll
    static void createSchema() {
        POSTGRES.withEnv("POSTGRES_PASSWORD", "test-only");
        POSTGRES.withExposedPorts(5432);
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/postgres",
                "postgres",
                "test-only");
        ResourceDatabasePopulator core = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"));
        core.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        core.execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("META-INF/sca4dj/schema/iot.sql")).execute(dataSource);
    }

    /** 为每个用例建立真实 Repository、事务及未发送 command。 */
    @BeforeEach
    void createCommand() throws Exception {
        new JdbcTemplate(dataSource).update("DELETE FROM iot_downlink_message WHERE message_id = 11");
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setArgNameBasedConstructorAutoMapping(true);
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/iot/DeviceDownlinkMapper.xml"));
        sessions = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        DeviceDownlinkMapper mapper = sessions.getMapper(DeviceDownlinkMapper.class);
        var acknowledgement = new DeviceResultAcknowledgementPolicy();
        var retry = new DefaultCommandRetryPolicy();
        repository = new DownlinkRepository(
                mapper,
                mock(DesiredPropertyMapper.class),
                new ThingDataJsonCodec(new ObjectMapper()),
                new DevicePolicyCatalog(List.of(acknowledgement), List.of(retry)),
                new SqlBatches(new SqlBatchProperties()));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        commands = new DeviceCommandService(
                mock(DeviceRepository.class),
                mock(ProductDefinitionCatalog.class),
                repository,
                mock(ThingModelRepository.class),
                new ThingValueValidator(),
                mock(IdGenerator.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                changes,
                publisher);
        mapper.insertDownlinkMessage(
                11L,
                21L,
                1L,
                DeviceDownlinkKind.COMMAND,
                "query",
                null,
                null,
                null,
                "{}",
                acknowledgement.code(),
                1000L,
                retry.code(),
                null);
    }

    /** 关闭本测试类唯一拥有的临时数据库。 */
    @AfterAll
    static void closeDatabase() {
        POSTGRES.close();
    }

    /** 使用 Spring 的销毁入口结束模板生命周期，避免调用不受支持的 close。 */
    @AfterEach
    void destroySessions() throws Exception {
        if (sessions != null)
            sessions.destroy();
    }

    @Test
    void rollsBackCancellationWithTheBusinessTransaction() {
        transactions.executeWithoutResult(status -> {
            assertThat(commands.cancel(11L)).isTrue();
            status.setRollbackOnly();
        });
        assertThat(repository.find(11L).orElseThrow().status()).isEqualTo(DeviceDownlinkStatus.PENDING);
        transactions.executeWithoutResult(status -> assertThat(commands.cancel(11L)).isTrue());
        assertThat(commands.cancel(11L)).isFalse();
        assertThat(repository.beginDispatch(11L, 1)).isFalse();
    }

    @Test
    void preservesAnAcknowledgementThatFinishedBeforeCancellation() {
        assertThat(repository.complete(11L, DeviceDownlinkStatus.SUCCEEDED, null, NOW)).isTrue();
        assertThat(commands.cancel(11L)).isFalse();
        assertThat(repository.find(11L).orElseThrow().status()).isEqualTo(DeviceDownlinkStatus.SUCCEEDED);
    }

    @Test
    void cancellationDuringSuccessfulPublishDoesNotRegisterAcknowledgementTimeout() {
        dispatchWithCancellation(false);
    }

    @Test
    void cancellationDuringFailedPublishDoesNotRegisterRetry() {
        dispatchWithCancellation(true);
    }

    /** 在协议发布与结果写回之间提交取消，验证两种发布结果均保留 CANCELLED。 */
    private void dispatchWithCancellation(boolean failPublish) {
        DeviceTransportRouter router = mock(DeviceTransportRouter.class);
        doAnswer(invocation -> {
            transactions.executeWithoutResult(status -> assertThat(commands.cancel(11L)).isTrue());
            if (failPublish)
                throw new IllegalStateException("测试协议发布失败");
            return null;
        }).when(router).publish(any());
        DistributedLock lock = new DistributedLock() {
            @Override
            public <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
                return task.get();
            }
        };
        var consumer = new DeviceDispatchHandler(
                repository,
                router,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                changes,
                lock,
                transactions,
                new JacksonMessageCodec<>(
                        DeviceDispatchMessage.CODE,
                        DeviceDispatchMessage.PROTOCOL_VERSION,
                        DeviceDispatchMessage.class,
                        new ObjectMapper()));
        consumer.handle(
                new IncomingMessage<>(
                        new MessageId(31L),
                        new DeviceDispatchMessage(11L, 1),
                        new Name("TEST"),
                        NOW,
                        false,
                        1));
        assertThat(repository.find(11L).orElseThrow().status()).isEqualTo(DeviceDownlinkStatus.CANCELLED);
        verifyNoInteractions(publisher);
    }
}
