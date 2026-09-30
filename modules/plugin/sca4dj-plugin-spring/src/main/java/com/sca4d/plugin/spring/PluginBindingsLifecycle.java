package com.sca4d.plugin.spring;

import com.sca4d.core.api.plugin.ApiBinding;
import com.sca4d.core.api.plugin.ApiContract;
import com.sca4d.core.api.plugin.PluginBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.nio.file.Path;

/** 在单例、迁移及系统身份准备完成后绑定代理 Bean，再加载必需插件。 */
final class PluginBindingsLifecycle implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(PluginBindingsLifecycle.class);

    private final ConfigurableListableBeanFactory factory;

    private final PluginProperties properties;

    private final PluginHost host;

    private boolean running;

    PluginBindingsLifecycle(PluginHost host, PluginProperties properties, ConfigurableListableBeanFactory factory) {
        this.host = host;
        this.properties = properties;
        this.factory = factory;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked", "resource"})
    public void start() {
        try {
            // 绑定 Spring 已完成代理的单例，插件调用仍经过原有事务和权限代理。
            for (String name : factory.getBeanDefinitionNames()) {
                if (!factory.containsSingleton(name))
                    continue;
                Object bean = factory.getBean(name);
                PluginBinding binding = AnnotatedElementUtils
                        .findMergedAnnotation(AopUtils.getTargetClass(bean), PluginBinding.class);
                if (binding == null
                        && factory.getBeanDefinition(name) instanceof RootBeanDefinition definition) {
                    var factoryMethod = definition.getResolvedFactoryMethod();
                    if (factoryMethod != null) {
                        binding = AnnotatedElementUtils.findMergedAnnotation(factoryMethod, PluginBinding.class);
                    }
                }
                if (binding == null)
                    continue;
                Class<?> declaration = binding.value();
                if (declaration.isInterface()) {
                    ApiContract contract = (ApiContract) Class.forName(
                            declaration.getName() + "Contract",
                            true,
                            declaration.getClassLoader()).getField("INSTANCE").get(null);
                    host.runtime().bind(contract.bind(bean, null));
                } else {
                    Class<?> bridge = Class
                            .forName(declaration.getName() + "ApiBinding", true, declaration.getClassLoader());
                    host.runtime().bind((ApiBinding) bridge.getMethod("bind", declaration).invoke(null, bean));
                }
            }
            // 宿主服务全部绑定后才执行插件初始化；必需扩展缺失时阻止应用启动。
            for (String artifact : properties.getArtifacts()) {
                host.runtime().replace(Path.of(artifact), properties.getDrainTimeout());
            }
            host.runtime().verifyRequired();
            running = true;
            LOG.info("App 插件已启动 | artifactCount={}", properties.getArtifacts().size());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            host.runtime().close();
            throw new IllegalStateException("App 必需插件装配失败", failure);
        }
    }

    @Override
    public void stop() {
        host.runtime().close();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE + 100;
    }
}
