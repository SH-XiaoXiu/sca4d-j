package com.sca4d.core.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.sca4d.core.spring.json.NameJacksonModule;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.retry.RetryRuleCodec;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** 配置 Spring Boot HTTP JSON 格式和脚手架内部稳定 JSON 映射器。 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
public class CoreJacksonAutoConfiguration {

    /** 创建 Core Jackson 自动配置。 */
    public CoreJacksonAutoConfiguration() {
    }

    /** 为业务 HTTP Mapper 安装公共名称值类型，不引入 Sca4djMapper 的内部协议配置。 */
    @Bean
    public @NotNull NameJacksonModule nameJacksonModule() {
        return new NameJacksonModule();
    }

    /**
     * 创建脚手架内部 Session wire、Redis 状态和消息共用的 JSON 映射器。
     *
     * @return 显式支持 Java Time、ISO 时间文本和严格类型绑定的内部映射器
     */
    @Bean(name = "sca4djObjectMapper", defaultCandidate = false)
    @Sca4djJson
    @ConditionalOnMissingBean(annotation = Sca4djJson.class)
    public @NotNull Sca4djMapper sca4djObjectMapper() {
        return new Sca4djMapper();
    }

    /**
     * @param mapper 脚手架内部共享 JSON 映射器
     * @return 持久化与内部消息共用的版本化重试规则 Codec
     */
    @Bean
    @ConditionalOnMissingBean(RetryRuleCodec.class)
    public @NotNull RetryRuleCodec retryRuleCodec(@Sca4djJson ObjectMapper mapper) {
        return new RetryRuleCodec(mapper);
    }

    /**
     * 把 `Long` 和 `long` 输出为十进制字符串；Jackson 读取时仍恢复为 Java 64 位整数。
     *
     * @return 应用于 Spring Boot 通用 `ObjectMapper` 的构建器定制器
     */
    @Bean
    public @NotNull Jackson2ObjectMapperBuilderCustomizer sca4djLongJsonCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
