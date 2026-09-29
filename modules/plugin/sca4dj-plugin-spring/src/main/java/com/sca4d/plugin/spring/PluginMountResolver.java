package com.sca4d.plugin.spring;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.ApiContract;
import com.sca4d.core.api.plugin.ApiKind;
import com.sca4d.core.api.plugin.PluginMount;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;

/** 将明确声明的注入点解析为生成客户端，其他注入保持 Spring 原有规则。 */
final class PluginMountResolver extends ContextAnnotationAutowireCandidateResolver {
    private final DefaultListableBeanFactory factory;

    PluginMountResolver(DefaultListableBeanFactory factory) {
        this.factory = factory;
    }

    @Override
    public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
        PluginMount mount = descriptor.getAnnotation(PluginMount.class);
        if (mount == null)
            return super.getLazyResolutionProxyIfNecessary(descriptor, beanName);
        PluginHost host = factory.getBean(PluginHost.class);
        ApiContract<?> contract = host.contract(TreeCode.of(mount.value()));
        if (contract.definition().kind() != ApiKind.EXTENSION
                || !descriptor.getDependencyType().isAssignableFrom(contract.type())) {
            throw new IllegalArgumentException("插件挂载类型与扩展声明不一致");
        }
        return contract.client(host.runtime(), mount.key().isEmpty() ? null : TreeCode.of(mount.key()));
    }
}
