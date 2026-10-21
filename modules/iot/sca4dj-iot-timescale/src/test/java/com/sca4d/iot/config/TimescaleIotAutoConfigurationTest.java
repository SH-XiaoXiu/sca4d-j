package com.sca4d.iot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.CoreMyBatisAutoConfiguration;
import com.sca4d.core.spring.retry.RetryRuleCodec;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/** 验证历史模块使用应用提供的数据源装配查询入口，且启动时不连接数据库执行结构变更。 */
class TimescaleIotAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(
                            CoreMyBatisAutoConfiguration.class,
                            TimescaleIotAutoConfiguration.class))
            .withUserConfiguration(Dependencies.class)
            .withPropertyValues("sca4dj.iot.timescale.enabled=true");

    @Test
    void createsHistoryQueriesWithoutInitializingTheDatabase() throws Exception {
        DataSource history = mock(DataSource.class);
        context.withBean(DataSource.class, () -> {
            DynamicRoutingDataSource routing = new DynamicRoutingDataSource(
                    List.of(() -> Map.of("main", mock(DataSource.class), IotTimescale.DATASOURCE, history)));
            routing.setPrimary("main");
            routing.setStrict(true);
            return routing;
        }).run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(DeviceHistoryStore.class);
            SqlSessionFactory factory = application.getBean("iotTimescaleSqlSessionFactory", SqlSessionFactory.class);
            assertThat(
                    factory.getConfiguration()
                            .hasStatement(
                                    "com.sca4d.iot.timescale.persistence.TimescaleMapper.selectRecentAcknowledgements"))
                    .isTrue();
        });
        verify(history, never()).getConnection();
    }

    @Test
    void rejectsAnUnroutedDataSource() {
        context.withBean(DataSource.class, () -> mock(DataSource.class))
                .run(
                        application -> assertThat(application.getStartupFailure())
                                .hasRootCauseMessage("IoT 历史存储必须使用动态数据源"));
    }

    @Test
    void rejectsAMissingHistoryRoute() {
        context.withBean(DataSource.class, () -> {
            DynamicRoutingDataSource routing = new DynamicRoutingDataSource(
                    List.of(() -> Map.of("main", mock(DataSource.class))));
            routing.setPrimary("main");
            return routing;
        })
                .run(
                        application -> assertThat(application.getStartupFailure()).hasRootCauseMessage(
                                "启用 IoT 历史存储必须配置 spring.datasource.dynamic.datasource.iot-history"));
    }

    /** 提供公共 MyBatis 装配所需的配置，不安装应用迁移或外部服务。 */
    @Configuration(proxyBeanMethods = false)
    static class Dependencies {

        @Bean
        @Sca4djJson
        Sca4djMapper mapper() {
            return new Sca4djMapper();
        }

        @Bean
        MybatisProperties mybatisProperties() {
            return new MybatisProperties();
        }

        @Bean
        RetryRuleCodec retryRules() {
            return mock(RetryRuleCodec.class);
        }

        @Bean
        SqlBatches batches() {
            return new SqlBatches(new SqlBatchProperties());
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
