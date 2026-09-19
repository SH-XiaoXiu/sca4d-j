package com.sca4d.iot.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.LongValueTypeHandler;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleTrigger;
import com.sca4d.iot.api.schedule.DeviceScheduledCommand;
import com.sca4d.iot.persistence.mapper.DeviceScheduleMapper;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 用隔离 PostgreSQL 验证定时规则、执行进展和 Device 范围。 */
@Testcontainers
class DeviceSchedulePersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static DeviceScheduleRepository schedules;

    private static JdbcTemplate jdbc;

    /** 启用 Repository 的实际事务和影响行数检查。 */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    static class Transactions {
    }

    /** 加载正式表结构和 Mapper，并注册调度状态枚举映射。 */
    @BeforeAll
    static void initialize() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/iot.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.getTypeHandlerRegistry()
                .register(Version.class, JdbcType.BIGINT, new LongValueTypeHandler<>(Version::new, Version::value));
        factory.setConfiguration(configuration);
        factory.setPlugins(new AffectedRowsInterceptor());
        factory.setMapperLocations(new ClassPathResource("mapper/iot/DeviceScheduleMapper.xml"));
        SqlSessionTemplate template = new SqlSessionTemplate(java.util.Objects.requireNonNull(factory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(AffectedRowsAspect.class);
        context.registerBean(
                DeviceScheduleRepository.class,
                () -> new DeviceScheduleRepository(
                        template.getMapper(DeviceScheduleMapper.class),
                        new com.sca4d.iot.model.ThingDataJsonCodec(new com.fasterxml.jackson.databind.ObjectMapper())));
        context.refresh();
        schedules = context.getBean(DeviceScheduleRepository.class);
    }

    /** 释放测试事务上下文。 */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("定时规则切换保留独立执行结果，状态更新保持版本和取消保护")
    void preservesTriggerAndExecutionIntent() {
        java.time.Instant now = java.time.Instant.parse("2026-09-15T00:00:00Z");
        var command = new DeviceScheduledCommand("query", java.util.Map.of());
        var cron = DeviceScheduleTrigger.cron("0 * * * * *", java.time.ZoneId.of("UTC"));
        schedules.add(101, 201, command, cron, java.time.Duration.ofMinutes(1), now);
        assertThat(schedules.findNextDue(now).orElseThrow().scheduleId()).isEqualTo(101);
        assertThat(
                schedules.recordExecutionAndAdvance(
                        101,
                        0,
                        DeviceScheduleStatus.ACTIVE,
                        now.plusSeconds(60),
                        now,
                        now,
                        DeviceScheduleExecutionStatus.FAILED,
                        null,
                        "UNAVAILABLE"))
                .isTrue();
        assertThat(schedules.changeStatus(101, 1, DeviceScheduleStatus.PAUSED, null)).isTrue();
        assertThat(schedules.findNextDue(now.plusSeconds(120))).isEmpty();
        assertThat(
                jdbc.queryForObject(
                        "SELECT last_failure_code FROM iot_device_schedule WHERE schedule_id = 101",
                        String.class))
                .isEqualTo("UNAVAILABLE");
        assertThat(
                schedules.replace(
                        101,
                        2,
                        command,
                        DeviceScheduleTrigger.once(now.plusSeconds(120)),
                        java.time.Duration.ofMinutes(1),
                        DeviceScheduleStatus.ACTIVE,
                        now.plusSeconds(120)))
                .isTrue();
        assertThat(
                jdbc.queryForObject(
                        "SELECT cron_expression IS NULL AND zone_id IS NULL FROM iot_device_schedule WHERE schedule_id = 101",
                        Boolean.class))
                .isTrue();
        assertThat(schedules.changeStatus(101, 2, DeviceScheduleStatus.PAUSED, null)).isFalse();
        assertThat(
                schedules.recordExecutionAndAdvance(
                        101,
                        3,
                        DeviceScheduleStatus.COMPLETED,
                        null,
                        now.plusSeconds(120),
                        now.plusSeconds(120),
                        DeviceScheduleExecutionStatus.ENQUEUED,
                        301L,
                        null))
                .isTrue();
        assertThat(
                jdbc.queryForObject(
                        "SELECT last_failure_code IS NULL AND next_fire_at IS NULL AND last_message_id = 301 FROM iot_device_schedule WHERE schedule_id = 101",
                        Boolean.class))
                .isTrue();
        assertThat(schedules.changeStatus(101, 4, DeviceScheduleStatus.CANCELLED, null)).isTrue();
        assertThat(
                schedules.replace(
                        101,
                        5,
                        command,
                        cron,
                        java.time.Duration.ofMinutes(1),
                        DeviceScheduleStatus.ACTIVE,
                        now.plusSeconds(180)))
                .isFalse();
        assertThat(schedules.findDeviceSchedules(201, new com.sca4d.core.api.query.PageRequest(1, 10)).getTotal())
                .isEqualTo(1);
        assertThat(schedules.findDeviceSchedules(202, new com.sca4d.core.api.query.PageRequest(1, 10)).getItems())
                .isEmpty();
    }
}
