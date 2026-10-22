package com.sca4d.iot.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAopConfiguration;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.name.Name;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.api.query.DeviceEvent;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/** 验证生产 Consumer 的实际代理入口选择历史数据库，且与主库事务独立提交或回滚。 */
@Testcontainers
class TimescaleRoutingIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private AnnotationConfigApplicationContext context;

    private JdbcTemplate primary;

    private JdbcTemplate history;

    /** 在临时 PostgreSQL 中建立第二个物理数据库。 */
    @BeforeAll
    static void createHistoryDatabase() {
        new JdbcTemplate(source(false)).execute("CREATE DATABASE iot_history");
    }

    /** 两个库分别建立同名表，只有实际 JDBC 路由正确才会得到预期数据。 */
    @BeforeEach
    void createContext() {
        primary = new JdbcTemplate(source(false));
        history = new JdbcTemplate(source(true));
        for (JdbcTemplate jdbc : List.of(primary, history)) {
            jdbc.execute("DROP TABLE IF EXISTS routed_messages");
            jdbc.execute("CREATE TABLE routed_messages(id BIGINT PRIMARY KEY)");
        }
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
    }

    /** 关闭代理和测试数据源，避免事务上下文影响其他用例。 */
    @AfterEach
    void closeContext() {
        context.close();
        assertThat(DynamicDataSourceContextHolder.peek()).isNull();
    }

    @Test
    void routesBothConsumerEntrypointsAndRollsBackFailedHistoryWrites() throws Exception {
        TimescaleTelemetryConsumer consumer = context.getBean(TimescaleTelemetryConsumer.class);
        consumer.handle(delivery(1));
        assertThatThrownBy(() -> consumer.handleEvent(delivery(9))).isInstanceOf(IllegalStateException.class);
        assertThat(primary.queryForList("SELECT id FROM routed_messages", Long.class)).isEmpty();
        assertThat(history.queryForList("SELECT id FROM routed_messages", Long.class)).containsExactly(1L);
    }

    @Test
    void suspendsTheMainTransactionBeforeWritingHistory() {
        TimescaleTelemetryConsumer consumer = context.getBean(TimescaleTelemetryConsumer.class);
        JdbcTemplate routed = context.getBean(JdbcTemplate.class);
        TransactionTemplate transaction = context.getBean(TransactionTemplate.class);
        transaction.executeWithoutResult(status -> {
            routed.update("INSERT INTO routed_messages VALUES (2)");
            consumer.handleEvent(delivery(2));
            status.setRollbackOnly();
        });
        assertThat(primary.queryForList("SELECT id FROM routed_messages", Long.class)).isEmpty();
        assertThat(history.queryForList("SELECT id FROM routed_messages", Long.class)).containsExactly(2L);
        DynamicDataSourceContextHolder.push("missing");
        try {
            assertThatThrownBy(() -> routed.queryForObject("SELECT 1", Integer.class))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    void routesRecentReadsToHistoryWhileMainTransactionRemainsActive() {
        primary.update("INSERT INTO routed_messages VALUES (1)");
        history.update("INSERT INTO routed_messages VALUES (2)");
        TimescaleDeviceHistoryStore store = context.getBean(TimescaleDeviceHistoryStore.class);
        JdbcTemplate routed = context.getBean(JdbcTemplate.class);
        context.getBean(TransactionTemplate.class).executeWithoutResult(status -> {
            assertThat(store.recentEvents(List.of(1L), 100).get(1L)).extracting(DeviceEvent::eventId)
                    .containsExactly(2L);
            assertThat(store.recentAcknowledgements(List.of(1L), 20).get(1L))
                    .extracting(DeviceAcknowledgement::messageId)
                    .containsExactly(2L);
            assertThat(routed.queryForList("SELECT id FROM routed_messages", Long.class)).containsExactly(1L);
        });
    }

    /** 返回只连接本测试容器的指定数据库的数据源。 */
    private static DataSource source(boolean history) {
        String url = history
                ? "jdbc:postgresql://" + POSTGRES.getHost() + ':' + POSTGRES.getMappedPort(5432) + "/iot_history"
                : POSTGRES.getJdbcUrl();
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** 创建带有固定 Telemetry 数据的投递，不依赖 Broker。 */
    private static IncomingMessage<Event<TelemetryBatch>> delivery(long id) {
        Instant time = Instant.parse("2026-09-26T00:00:00Z");
        TelemetryBatch batch = new TelemetryBatch(1, 1, List.of(new TelemetrySample("temperature", 20, time, time)));
        return new IncomingMessage<>(
                Id.of(id),
                new Event<>(IotMessageTypes.TELEMETRY_REPORTED, null, time, batch),
                new Name("TEST"),
                time,
                false,
                1);
    }

    /** 只安装成熟库的 DS 切面和 Spring 事务；使用真实数据库验证生产 Consumer。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement
    @Import(DynamicDataSourceAopConfiguration.class)
    static class TestConfiguration {

        /** 用两个实际数据库配置成熟库的严格路由。 */
        @Bean
        DataSource dataSource() {
            DynamicRoutingDataSource router = new DynamicRoutingDataSource(
                    List.of(() -> Map.of("main", source(false), "iot-history", source(true))));
            router.setPrimary("main");
            router.setStrict(true);
            return router;
        }

        /** 返回通过路由数据源执行 SQL 的 JDBC 操作。 */
        @Bean
        JdbcTemplate jdbc(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        /** 返回两个数据库共用的 Spring 事务管理器。 */
        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        /** 供主库回滚场景显式开启外层事务。 */
        @Bean
        TransactionTemplate transactionTemplate(DataSourceTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        /** 替换物模型 SQL 细节，但实际写入与失败仍发生在生产 Consumer 开启的 JDBC 事务内。 */
        @Bean
        TimescaleRepository repository(JdbcTemplate jdbc) {
            TimescaleRepository repository = mock(TimescaleRepository.class);
            doAnswer(invocation -> {
                long id = invocation.getArgument(0);
                jdbc.update("INSERT INTO routed_messages VALUES (?)", id);
                if (id == 9) {
                    throw new IllegalStateException("history write failed");
                }
                return null;
            }).when(repository).addTelemetry(anyLong(), any(TelemetryBatch.class));
            doAnswer(
                    invocation -> Map.of(
                            1L,
                            List.of(
                                    new DeviceEvent(
                                            jdbc.queryForObject("SELECT id FROM routed_messages", Long.class),
                                            1L,
                                            "status",
                                            Map.of(),
                                            Instant.EPOCH,
                                            Instant.EPOCH))))
                    .when(repository)
                    .findRecentEvents(anyCollection(), anyInt());
            doAnswer(
                    invocation -> Map.of(
                            1L,
                            List.of(
                                    new DeviceAcknowledgement(
                                            jdbc.queryForObject("SELECT id FROM routed_messages", Long.class),
                                            "OK",
                                            Map.of(),
                                            "DEVICE",
                                            null,
                                            Instant.EPOCH,
                                            Instant.EPOCH))))
                    .when(repository)
                    .findRecentAcknowledgements(anyCollection(), anyInt());
            return repository;
        }

        /** 注册实际历史存储，验证新增只读入口也经过数据源与事务代理。 */
        @Bean
        TimescaleDeviceHistoryStore historyStore(TimescaleRepository repository) {
            return new TimescaleDeviceHistoryStore(repository);
        }

        /** 注册实际生产 Consumer，调用方获得 Spring 代理。 */
        @Bean
        TimescaleTelemetryConsumer consumer(TimescaleRepository repository) {
            return new TimescaleTelemetryConsumer(repository);
        }
    }
}
