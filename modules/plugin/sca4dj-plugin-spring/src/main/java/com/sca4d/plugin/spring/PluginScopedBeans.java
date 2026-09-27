package com.sca4d.plugin.spring;

import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.core.api.plugin.PluginScope;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** 将 Bean 工厂方法的作用域声明应用到实际对象，原实现无需感知插件。 */
final class PluginScopedBeans implements BeanPostProcessor, Ordered {
    private final ConfigurableListableBeanFactory factory;
    private final ObjectProvider<PluginRuntime> runtime;
    PluginScopedBeans(ConfigurableListableBeanFactory factory, ObjectProvider<PluginRuntime> runtime) {
        this.factory = factory;
        this.runtime = runtime;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (!factory.containsBeanDefinition(name)
                || !(factory.getBeanDefinition(name) instanceof RootBeanDefinition definition)
                || definition.getResolvedFactoryMethod() == null
                || !AnnotatedElementUtils.hasAnnotation(definition.getResolvedFactoryMethod(), PluginScope.class)) {
            return bean;
        }
        ProxyFactory proxy = new ProxyFactory(bean);
        if (bean.getClass().getInterfaces().length == 0) proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation ->
                PluginScopeInvocation.invoke(runtime.getObject(), invocation::proceed));
        return proxy.getProxy(bean.getClass().getClassLoader());
    }
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
