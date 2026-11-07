package com.sca4d.app.server.iot;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.sca4d.iot.config.IotTimescale;
import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** 在启用 IoT 历史库时对 iot-history 数据源执行应用自己维护的历史库迁移。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sca4dj.iot.timescale", name = "enabled", havingValue = "true")
public class IotHistoryMigrationConfiguration {

    /** 历史库迁移位置；V1 基线复制自 sca4dj-iot-timescale 的 META-INF/sca4dj/timescale/schema.sql。 */
    private static final String MIGRATION_LOCATIONS = "classpath:db/iot-history";

    /**
     * 创建并立即执行 IoT 历史库迁移；迁移失败时应用停止启动。
     *
     * @param dataSource 应用动态路由数据源
     * @return 已经完成迁移的 IoT 历史库 Flyway
     */
    @Bean(defaultCandidate = false)
    @IotTimescale
    Flyway iotHistoryFlyway(@NotNull DataSource dataSource) {
        if (!(dataSource instanceof DynamicRoutingDataSource routing)) {
            throw new IllegalStateException("IoT 历史库迁移必须使用动态数据源");
        }
        DataSource historyDataSource = routing.getDataSource(IotTimescale.DATASOURCE);
        if (historyDataSource == null) {
            throw new IllegalStateException("启用 IoT 历史库必须配置 spring.datasource.dynamic.datasource.iot-history");
        }
        Flyway flyway = Flyway.configure()
                .dataSource(historyDataSource)
                .locations(MIGRATION_LOCATIONS)
                .load();
        flyway.migrate();
        return flyway;
    }
}
