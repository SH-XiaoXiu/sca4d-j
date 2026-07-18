package com.sca4d.config;

import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.config.properties.Sca4djIamCleanupProperties;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.config.properties.Sca4djSchedulerProperties;
import org.jetbrains.annotations.NotNull;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.util.Properties;

/** Quartz JDBC 集群的确定性运行配置。 */
@Configuration
@EnableConfigurationProperties({
        Sca4djSchedulerProperties.class,
        Sca4djIamCleanupProperties.class,
        Sca4djMessagingProperties.class,
        Sca4djFileProperties.class
})
public class SchedulerConfig {

    /**
     * 把类型化 Duration 配置转换为 Quartz 所需的毫秒配置，避免维护第二套数值环境变量。
     *
     * @param schedulerProperties Scheduler 集群配置
     * @return SchedulerFactoryBean 定制入口
     */
    @Bean
    public @NotNull SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(
            @NotNull Sca4djSchedulerProperties schedulerProperties,
            @NotNull AutowireCapableBeanFactory beanFactory) {
        return schedulerFactory -> {
            Properties quartz = new Properties();
            quartz.setProperty("org.quartz.scheduler.instanceId", "AUTO");
            quartz.setProperty("org.quartz.jobStore.class", LocalDataSourceJobStore.class.getName());
            quartz.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
            quartz.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
            quartz.setProperty("org.quartz.jobStore.isClustered", "true");
            quartz.setProperty("org.quartz.jobStore.acquireTriggersWithinLock", "true");
            quartz.setProperty(
                    "org.quartz.jobStore.clusterCheckinInterval",
                    Long.toString(schedulerProperties.getClusterCheckinInterval().toMillis()));
            quartz.setProperty(
                    "org.quartz.jobStore.misfireThreshold",
                    Long.toString(schedulerProperties.getMisfireThreshold().toMillis()));
            quartz.setProperty("org.quartz.threadPool.threadCount", Integer.toString(schedulerProperties.getThreadCount()));
            schedulerFactory.setSchedulerName(schedulerProperties.getSchedulerName());
            schedulerFactory.setQuartzProperties(quartz);
            schedulerFactory.setJobFactory(new SpringBeanJobFactory() {
                @Override
                protected @NotNull Object createJobInstance(@NotNull TriggerFiredBundle bundle) throws Exception {
                    Object job = super.createJobInstance(bundle);
                    beanFactory.autowireBean(job);
                    return job;
                }
            });
        };
    }
}
