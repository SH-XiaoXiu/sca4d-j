package com.sca4d.config;

import com.sca4d.common.id.ClusterIdGenerator;
import com.sca4d.common.id.ClusterIds;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.config.properties.Sca4djIdProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集群 ID 配置。
 */
@Configuration
@EnableConfigurationProperties(Sca4djIdProperties.class)
public class IdConfig {

    /**
     * 注册默认集群 ID 生成器。
     *
     * @param properties 集群 ID 配置
     * @return ID 生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull IdGenerator idGenerator(@NotNull Sca4djIdProperties properties) {
        ClusterIdGenerator generator = new ClusterIdGenerator(properties.getWorkerId(), properties.getDataCenterId());
        ClusterIds.setGenerator(generator);
        return generator;
    }
}
