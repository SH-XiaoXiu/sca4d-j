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

/** 校验 `sca4dj.instance.id` 并生成本进程的功能启用快照（`InstanceCapabilities`）。 */
@AutoConfiguration
@EnableConfigurationProperties(InstanceProperties.class)
public class InstanceAutoConfiguration {

    /** 注册 `InstanceCapabilityFilter`，在认证前拒绝当前实例未启用功能的 API；该过滤器不授予任何业务权限。 */
    @Bean
    @ConditionalOnWebApplication(type = SERVLET)
    public @NotNull FilterRegistrationBean<InstanceCapabilityFilter> instanceCapabilityFilter(
            @NotNull InstanceCapabilities capabilities) {
        FilterRegistrationBean<InstanceCapabilityFilter> registration = new FilterRegistrationBean<>(
                new InstanceCapabilityFilter(capabilities));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * 校验实例标识并把配置的功能编码转换为 `TreeCode`；标识不合法时启动失败。
     *
     * @param properties 实例配置，`capabilities` 为空时启用全部已声明功能
     * @param definitions 应用声明的全部功能定义
     * @return 当前进程的功能启用快照
     * @throws IllegalStateException `sca4dj.instance.id` 不是 1 到 128 位字母、数字、点、下划线或连字符时抛出
     */
    @Bean
    public @NotNull InstanceCapabilities instanceCapabilities(
            @NotNull InstanceProperties properties,
            @NotNull List<InstanceCapability> definitions) {
        if (properties.getId() == null || !properties.getId().matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalStateException("sca4dj.instance.id 必须是 1 到 128 位字母、数字、点、下划线或连字符");
        }
        List<String> selected = properties.getCapabilities();
        return new InstanceCapabilities(
                definitions,
                selected == null
                        ? null
                        : selected.stream()
                                .map(TreeCode::of)
                                .toList());
    }
}
