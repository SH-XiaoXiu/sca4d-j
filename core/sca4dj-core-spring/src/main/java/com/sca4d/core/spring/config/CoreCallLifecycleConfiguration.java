package com.sca4d.core.spring.config;

import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallInvoker;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallLifecycleConfiguration;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.call.CallPreCommitTransactionInstaller;
import com.sca4d.core.spring.call.CallPreCommitTransactionListener;
import com.sca4d.core.spring.call.SpelCallValueResolver;
import com.sca4d.core.spring.call.SpringCallValueResolverLookup;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 注册调用生命周期、AOP 桥接和事务预提交处理。 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class CoreCallLifecycleConfiguration {

    /** @param applicationContext Spring 容器 @return 按 Bean 类型查找的值解析器目录 */
    @Bean
    CallValueResolverLookup callValueResolverLookup(ApplicationContext applicationContext) {
        return new SpringCallValueResolverLookup(applicationContext);
    }

    /** @param applicationContext Spring 容器 @return 默认注解表达式解析器 */
    @Bean
    @ConditionalOnMissingBean(SPICallValueResolver.class)
    SPICallValueResolver spiCallValueResolver(ApplicationContext applicationContext) {
        return new SpelCallValueResolver(applicationContext);
    }

    /** @param participants 功能和应用注册的阶段参与者 @param customizers 模块和应用补充的依赖 @return 待校验的生命周期配置 */
    @Bean
    @ConditionalOnMissingBean
    CallLifecycleConfiguration callLifecycleConfiguration(
            List<CallLifecycle.Participant> participants,
            List<CallLifecycleConfiguration.Customizer> customizers) {
        CallLifecycleConfiguration configuration = new CallLifecycleConfiguration(participants);
        customizers.forEach(customizer -> customizer.configure(configuration));
        return configuration;
    }

    /** @param configuration 已登记阶段和依赖的配置 @return 已完成依赖校验的调用执行器 */
    @Bean
    CallInvoker callInvoker(CallLifecycleConfiguration configuration) {
        return new CallInvoker(configuration);
    }

    /** @param invoker 调用执行器 @param declarations 功能所属注解注册 @param clock 入口时钟 @return Spring AOP 桥接 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor callLifecycleAdvisor(
            ObjectProvider<CallInvoker> invoker,
            ObjectProvider<CallLifecycleAdvisor.AnnotationBinding<?>> declarations,
            ObjectProvider<CallLifecycleAdvisor.MethodBinding> methodBindings,
            ObjectProvider<Clock> clock) {
        return new CallLifecycleAdvisor(invoker, declarations, methodBindings, clock);
    }

    /** 注册 Core 自己定义的匿名访问注解。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<AnonymousAccess> anonymousCallBinding() {
        return () -> AnonymousAccess.class;
    }

    /** 注册无内置注解但显式要求进入调用生命周期的方法。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<CallLifecycle.Intercepted> interceptedCallBinding() {
        return () -> CallLifecycle.Intercepted.class;
    }

    /** 系统注解只负责自主入口身份，不向已有用户调用赋予额外权限。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CallLifecycleAdvisor.AnnotationBinding<SystemExecution> systemCallBinding() {
        return new CallLifecycleAdvisor.AnnotationBinding<>() {
            @Override
            public @NonNull Class<SystemExecution> annotationType() {
                return SystemExecution.class;
            }

            @Override
            public @NonNull Optional<Call> createCall(String callId, Instant startedAt, SystemExecution annotation) {
                return Optional
                        .of(Call.system(callId, startedAt, SystemPrincipals.require(TreeCode.of(annotation.value()))));
            }
        };
    }

    /** @return 事务提交前预提交条件监听器 */
    @Bean
    CallPreCommitTransactionListener callPreCommitTransactionListener() {
        return new CallPreCommitTransactionListener();
    }

    /**
     * @param listener 事务提交前预提交条件监听器
     * @return 把监听器安装到事务管理器的 Bean 后处理器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static CallPreCommitTransactionInstaller callPreCommitTransactionInstaller(
            ObjectProvider<CallPreCommitTransactionListener> listener) {
        return new CallPreCommitTransactionInstaller(listener);
    }
}
