package com.sca4d.iot.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.scheduling.IntervalSchedule;
import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.MisfirePolicy;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.AdditionalMyBatisSessionFactoryBuilder;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import com.sca4d.iot.telemetry.TimescaleDeviceHistoryStore;
import com.sca4d.iot.telemetry.TimescaleEventConsumer;
import com.sca4d.iot.telemetry.TimescaleRetentionTask;
import com.sca4d.iot.telemetry.TimescaleTelemetryConsumer;
import com.sca4d.iot.timescale.persistence.TimescaleMapper;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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

    /** @return 只加载 IoT 历史 Mapper XML 的独立 SqlSessionFactory */
    @Bean(defaultCandidate = false)
    @IotTimescale
    SqlSessionFactory iotTimescaleSqlSessionFactory(
            @NotNull DataSource dataSource,
            @NotNull AdditionalMyBatisSessionFactoryBuilder builder) throws Exception {
        if (!(dataSource instanceof DynamicRoutingDataSource routing)) {
            throw new IllegalStateException("IoT 历史存储必须使用动态数据源");
        }
        if (!routing.getDataSources().containsKey(IotTimescale.DATASOURCE)) {
            throw new IllegalStateException("启用 IoT 历史存储必须配置 spring.datasource.dynamic.datasource.iot-history");
        }
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
            @Sca4djJson @NotNull Sca4djMapper objectMapper,
            @NotNull SqlBatches sqlBatches,
            @NotNull IotTimescaleProperties properties) {
        properties.validate();
        return new TimescaleRepository(mapper, objectMapper, sqlBatches, properties.getMaxQueryResults());
    }

    /** @return 消费 reported Telemetry 并直接写入 TimescaleDB 的 Consumer */
    @Bean
    @ConditionalOnCapability(IotCapabilities.TRANSPORT)
    TimescaleTelemetryConsumer timescaleTelemetryConsumer(@NotNull TimescaleRepository repository) {
        return new TimescaleTelemetryConsumer(repository);
    }

    /** @return 消费物模型和 IoT 状态机事件并直接写入 TimescaleDB 的 Consumer */
    @Bean
    @ConditionalOnCapability(IotCapabilities.TRANSPORT)
    TimescaleEventConsumer timescaleEventConsumer(@NotNull TimescaleRepository repository) {
        return new TimescaleEventConsumer(repository);
    }

    /** @return 默认 Device 历史查询存储；应用提供其他存储 Bean 时保留应用 Bean */
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
    JobSchedule<Void, Void> timescaleRetentionSchedule(
            @Qualifier("timescaleRetentionJob") @NotNull DirectJob<Void, Void> job,
            @NotNull IotTimescaleProperties properties) {
        return new JobSchedule<>(
                new ScheduleKey("IOT/TIMESCALE/RETENTION"),
                1,
                job,
                null,
                new IntervalSchedule(properties.getCleanupInterval(), properties.getCleanupInterval()),
                MisfirePolicy.FIRE_ONCE_NOW,
                OverlapPolicy.SKIP_IF_RUNNING);
    }

    @Bean
    DirectJob<Void, Void> timescaleRetentionJob(@NotNull TimescaleRetentionTask task) {
        return DirectJob.unrecorded(
                new JobKey("IOT/TIMESCALE/RETENTION"),
                1,
                TreeCode.ofColon(IotCapabilities.WORKER),
                task::execute);
    }
}
