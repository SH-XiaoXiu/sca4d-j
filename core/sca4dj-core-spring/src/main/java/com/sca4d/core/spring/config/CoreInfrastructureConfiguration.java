package com.sca4d.core.spring.config;

import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.region.PhoneNumberCondition;
import com.sca4d.core.api.region.RegionCatalog;
import com.sca4d.core.spring.id.SnowflakeIdGenerator;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.redis.RedisBatches;
import com.sca4d.core.spring.redis.RedisPipelineProperties;
import com.sca4d.core.spring.validation.PhoneNumberConditionRegistry;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import java.time.Clock;
import java.util.List;

/** 注册 Core API 使用的时间、标识、地区和分批执行入口。 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CoreInfrastructureConfiguration {

    /** @return 使用 UTC 时间线的系统时钟 */
    @Bean
    @ConditionalOnMissingBean
    Clock coreClock() {
        return Clock.systemUTC();
    }

    /**
     * @param properties 雪花 ID 节点配置
     * @return 默认雪花 ID 生成器
     */
    @Bean
    @ConditionalOnMissingBean
    IdGenerator idGenerator(IdProperties properties) {
        return new SnowflakeIdGenerator(properties.getNodeId());
    }

    /** @return 使用 libphonenumber 单例元数据的地区目录 */
    @Bean
    @ConditionalOnMissingBean
    RegionCatalog regionCatalog() {
        return RegionCatalog.standard();
    }

    /** @param properties SQL 批次限制 @return 公共 SQL 分批执行器 */
    @Bean
    @ConditionalOnMissingBean
    SqlBatches sqlBatches(SqlBatchProperties properties) {
        return new SqlBatches(properties);
    }

    /** @param properties Redis pipeline 限制 @return 公共 Redis 分批执行器 */
    @Bean
    @ConditionalOnMissingBean
    RedisBatches redisBatches(RedisPipelineProperties properties) {
        return new RedisBatches(properties);
    }

    /** @param conditions 应用显式声明的电话号码条件 Bean @return 已检查重复类型的条件目录 */
    @Bean
    PhoneNumberConditionRegistry phoneNumberConditionRegistry(List<PhoneNumberCondition> conditions) {
        return new PhoneNumberConditionRegistry(conditions);
    }
}
