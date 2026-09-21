package com.sca4d.iot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.AdditionalMyBatisSessionFactoryBuilder;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import com.sca4d.iot.telemetry.TimescaleDeviceHistoryStore;
import com.sca4d.iot.telemetry.TimescaleEventConsumer;
import com.sca4d.iot.telemetry.TimescaleRetentionTask;
import com.sca4d.iot.telemetry.TimescaleTelemetryConsumer;
import com.sca4d.iot.timescale.persistence.TimescaleMapper;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

/** 在启用时为 IoT Telemetry 和事件创建独立 TimescaleDB 存储及消息 Consumer。 */
@AutoConfiguration(after = MybatisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "sca4dj.iot.timescale", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IotTimescaleProperties.class)
public class TimescaleIotAutoConfiguration {

    private static final String MAPPER_LOCATIONS = "classpath*:META-INF/sca4dj/mybatis/iot-history/*.xml";

    /** @return 使用模块配置创建的独立 TimescaleDB DataSource */
    @Bean(defaultCandidate = false)
    @IotTimescale
    DataSource iotTimescaleDataSource(@NotNull IotTimescaleProperties properties) {
        IotTimescaleProperties checked = Objects.requireNonNull(properties, "IoT Timescale 配置不能为空");
        checked.validate();
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl(checked.getUrl());
        dataSourceProperties.setUsername(checked.getUsername());
        dataSourceProperties.setPassword(checked.getPassword());
        dataSourceProperties.setDriverClassName(checked.getDriverClassName());
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }

    /** @return 只迁移 IoT 模块版本化高频表资源的独立 Flyway */
    @Bean(initMethod = "migrate", defaultCandidate = false)
    @IotTimescale
    Flyway iotTimescaleFlyway(@IotTimescale @NotNull DataSource dataSource) {
        return Flyway.configure()
                .dataSource(Objects.requireNonNull(dataSource, "IoT Timescale DataSource 不能为空"))
                .locations("classpath:META-INF/sca4dj/timescale/migration")
                .load();
    }

    /** @return 只加载 IoT 历史 Mapper XML 的独立 SqlSessionFactory */
    @Bean(defaultCandidate = false)
    @IotTimescale
    SqlSessionFactory iotTimescaleSqlSessionFactory(
            @IotTimescale @NotNull DataSource dataSource,
            @IotTimescale @NotNull Flyway migration,
            @NotNull AdditionalMyBatisSessionFactoryBuilder builder) throws Exception {
        Objects.requireNonNull(migration, "IoT Timescale Flyway 不能为空");
        Resource[] mapperLocations = new PathMatchingResourcePatternResolver().getResources(MAPPER_LOCATIONS);
        if (mapperLocations.length == 0) {
            throw new IllegalStateException("IoT Timescale Mapper XML 不存在: " + MAPPER_LOCATIONS);
        }
        List<Resource> mapperResources = Arrays.asList(mapperLocations);
        return builder.build(
                Objects.requireNonNull(dataSource, "IoT Timescale DataSource 不能为空"),
                mapperResources);
    }

    /** @return 只使用 TimescaleDB 会话工厂的 SqlSessionTemplate */
    @Bean(defaultCandidate = false)
    @IotTimescale
    SqlSessionTemplate iotTimescaleSqlSessionTemplate(
            @IotTimescale @NotNull SqlSessionFactory factory) {
        return new SqlSessionTemplate(Objects.requireNonNull(factory, "IoT Timescale SqlSessionFactory 不能为空"));
    }

    /** @return TimescaleDB 写入和 drop_chunks 使用的独立事务管理器 */
    @Bean(defaultCandidate = false)
    @IotTimescale
    PlatformTransactionManager iotTimescaleTransactionManager(
            @IotTimescale @NotNull DataSource dataSource) {
        return new DataSourceTransactionManager(
                Objects.requireNonNull(dataSource, "IoT Timescale DataSource 不能为空"));
    }

    /** @return 只通过 TimescaleDB SqlSessionTemplate 执行 SQL 的 Mapper */
    @Bean(defaultCandidate = false)
    @IotTimescale
    TimescaleMapper timescaleMapper(@IotTimescale @NotNull SqlSessionTemplate template) {
        return Objects.requireNonNull(template, "IoT Timescale SqlSessionTemplate 不能为空")
                .getMapper(TimescaleMapper.class);
    }

    /** @return 隐藏 Timescale Mapper 和影响行数的 Repository */
    @Bean
    TimescaleRepository timescaleRepository(
            @IotTimescale @NotNull TimescaleMapper mapper,
            @Sca4djJson @NotNull ObjectMapper objectMapper,
            @NotNull SqlBatches sqlBatches,
            @NotNull IotTimescaleProperties properties) {
        properties.validate();
        return new TimescaleRepository(mapper, objectMapper, sqlBatches, properties.getMaxQueryResults());
    }

    /** @return 消费 reported Telemetry 并直接写入 TimescaleDB 的 Consumer */
    @Bean
    TimescaleTelemetryConsumer timescaleTelemetryConsumer(@NotNull TimescaleRepository repository) {
        return new TimescaleTelemetryConsumer(repository);
    }

    /** @return 消费物模型和 IoT 状态机事件并直接写入 TimescaleDB 的 Consumer */
    @Bean
    TimescaleEventConsumer timescaleEventConsumer(@NotNull TimescaleRepository repository) {
        return new TimescaleEventConsumer(repository);
    }

    /** @return 默认 Device 历史查询存储；应用提供其他存储时保留应用选择 */
    @Bean
    @ConditionalOnMissingBean(DeviceHistoryStore.class)
    DeviceHistoryStore timescaleDeviceHistoryStore(@NotNull TimescaleRepository repository) {
        return new TimescaleDeviceHistoryStore(repository);
    }

    /** @return 使用配置保留期执行 TimescaleDB drop_chunks 的集群任务 */
    @Bean
    TimescaleRetentionTask timescaleRetentionTask(
            @NotNull TimescaleRepository repository,
            @NotNull Clock clock,
            @NotNull IotTimescaleProperties properties) {
        properties.validate();
        return new TimescaleRetentionTask(
                repository,
                clock,
                properties.getTelemetryRetention(),
                properties.getEventRetention());
    }

    /** @return 按应用配置间隔清理 TimescaleDB 完整历史 chunk 的集群计划 */
    @Bean
    ClusterTaskSchedule timescaleRetentionSchedule(
            @NotNull TimescaleRetentionTask task,
            @NotNull IotTimescaleProperties properties) {
        return new ClusterTaskSchedule(
                new ScheduleKey("IOT/TIMESCALE/RETENTION"),
                1,
                task,
                new IntervalSchedule(properties.getCleanupInterval(), properties.getCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW);
    }
}
