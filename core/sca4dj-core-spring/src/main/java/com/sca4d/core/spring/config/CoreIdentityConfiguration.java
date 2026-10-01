package com.sca4d.core.spring.config;

import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.identity.PrincipalSelectionCallInterceptor;
import com.sca4d.core.spring.identity.PrincipalSelectionInputResolver;
import com.sca4d.core.spring.identity.PrincipalSelectionInterceptor;
import com.sca4d.core.spring.identity.PrincipalSelectionInvoker;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/** 注册调用身份检查和 HTTP 业务身份选择。 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CoreIdentityConfiguration {

    /** @return 调用认证及主体前提检查 */
    @Bean
    IdentityCallInterceptor identityCallInterceptor() {
        return new IdentityCallInterceptor();
    }

    /** @return 主体选择完成后检查权限和审计声明的业务主体前提 */
    @Bean
    IdentityCallInterceptor.PrincipalRequirement principalRequirement() {
        return new IdentityCallInterceptor.PrincipalRequirement();
    }

    /** @return HTTP 业务身份选择输入解析器 */
    @Bean
    PrincipalSelectionInputResolver principalSelectionInputResolver() {
        return new PrincipalSelectionInputResolver();
    }

    /** @return 使用 Spring JavaBean 绑定的类型化业务身份选择调用器 */
    @Bean
    PrincipalSelectionInvoker principalSelectionInvoker() {
        return new PrincipalSelectionInvoker();
    }

    /**
     * @param applicationContext Spring 容器
     * @param inputResolver HTTP 选择输入解析器
     * @param invoker 类型化选择器调用器
     * @return Controller 业务身份选择切面
     */
    @Bean
    PrincipalSelectionCallInterceptor principalSelectionCallInterceptor(
            ApplicationContext applicationContext,
            PrincipalSelectionInputResolver inputResolver,
            PrincipalSelectionInvoker invoker) {
        return new PrincipalSelectionCallInterceptor(applicationContext, inputResolver, invoker);
    }

    /** 注册业务身份选择声明；运行时选择由主体选择参与者负责。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<CurrentPrincipal> currentPrincipalCallBinding() {
        return () -> CurrentPrincipal.class;
    }

    /**
     * @param applicationContext Spring 容器
     * @param inputResolver HTTP 选择输入解析器
     * @param invoker 类型化选择器调用器
     * @return MVC 参数绑定前的业务身份选择拦截器
     */
    @Bean
    PrincipalSelectionInterceptor principalSelectionInterceptor(
            ApplicationContext applicationContext,
            PrincipalSelectionInputResolver inputResolver,
            PrincipalSelectionInvoker invoker) {
        return new PrincipalSelectionInterceptor(applicationContext, inputResolver, invoker);
    }
}
