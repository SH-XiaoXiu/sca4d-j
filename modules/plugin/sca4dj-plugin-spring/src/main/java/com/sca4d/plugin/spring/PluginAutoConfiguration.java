package com.sca4d.plugin.spring;

import com.sca4d.core.api.plugin.PluginConfiguration;
import com.sca4d.core.api.plugin.PluginRuntime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 把 Core 插件机制接入宿主 Spring，具体业务绑定由 App 声明。 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sca4dj.plugin", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PluginProperties.class)
public class PluginAutoConfiguration {
    /** 用惰性运行入口为 Bean 工厂声明安装作用域，避免提前构造业务单例。 */
    @Bean
    static PluginScopedBeans pluginScopedBeans(
            ConfigurableListableBeanFactory factory,
            ObjectProvider<PluginRuntime> runtime) {
        return new PluginScopedBeans(factory, runtime);
    }

    /** 在实例构造前安装明确的插件挂载规则。 */
    @Bean
    static BeanFactoryPostProcessor pluginMountResolverInstaller() {
        return factory -> {
            DefaultListableBeanFactory listable = (DefaultListableBeanFactory) factory;
            PluginMountResolver resolver = new PluginMountResolver(listable);
            resolver.setBeanFactory(listable);
            listable.setAutowireCandidateResolver(resolver);
        };
    }

    /** 根据生成描述创建宿主插件运行组成。 */
    @Bean
    PluginHost pluginHost(PluginProperties properties, PluginConfiguration<?> configuration) throws Exception {
        return new PluginHost(properties, Thread.currentThread().getContextClassLoader(), configuration);
    }

    /** 向 App 注入 Core 管理接口。 */
    @Bean
    PluginRuntime pluginRuntime(PluginHost host) {
        return host.runtime();
    }

    /** 将插件代次作用域挂载到统一 Call 生命周期。 */
    @Bean
    PluginScopeCallInterceptor pluginScopeCallInterceptor(PluginRuntime runtime) {
        return new PluginScopeCallInterceptor(runtime);
    }

    /** 先加载必需插件，再允许正常业务进入。 */
    @Bean
    PluginBindingsLifecycle pluginBindingsLifecycle(
            PluginHost host,
            PluginProperties properties,
            ConfigurableListableBeanFactory factory) {
        return new PluginBindingsLifecycle(host, properties, factory);
    }
}
