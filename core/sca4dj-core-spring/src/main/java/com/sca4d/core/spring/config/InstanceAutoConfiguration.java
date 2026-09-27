package com.sca4d.core.spring.config;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.runtime.InstanceCapabilities;
import com.sca4d.core.api.runtime.InstanceCapability;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.util.List;

/** 校验当前进程标识并固化全部能力声明及启用选择。 */
@AutoConfiguration
@EnableConfigurationProperties(InstanceProperties.class)
public class InstanceAutoConfiguration {

    /** 在业务认证前拒绝当前实例未启用的 API；能力检查不授予任何业务权限。 */
    @Bean
    @ConditionalOnWebApplication(type = SERVLET)
    public @NotNull FilterRegistrationBean<InstanceCapabilityFilter> instanceCapabilityFilter(
            @NotNull InstanceCapabilities capabilities) {
        FilterRegistrationBean<InstanceCapabilityFilter> registration = new FilterRegistrationBean<>(
                new InstanceCapabilityFilter(capabilities));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /** 启动时拒绝未知能力和非法实例标识。 */
    @Bean
    public @NotNull InstanceCapabilities instanceCapabilities(
            @NotNull InstanceProperties properties,
            @NotNull List<InstanceCapability> definitions) {
        if (properties.getId() == null || !properties.getId().matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalStateException("sca4dj.instance.id 必须是 1 到 128 位字母、数字、点、下划线或连字符");
        }
        List<String> selected = properties.getEnabledCapabilities();
        return new InstanceCapabilities(
                definitions,
                selected == null
                        ? null
                        : selected.stream()
                                .map(TreeCode::of)
                                .toList());
    }
}
