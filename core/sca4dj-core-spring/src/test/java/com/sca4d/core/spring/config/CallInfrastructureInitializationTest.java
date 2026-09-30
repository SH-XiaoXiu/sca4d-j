package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.call.CallPreCommitTransactionListener;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/** 验证读取 Call Advisor 不会在后处理器注册期间创建执行链和事务业务对象。 */
class CallInfrastructureInitializationTest {

    @Test
    void preservesPostProcessingAndTransactionsWhenAdvisorIsReadEarly() {
        Logger checker = (Logger) LoggerFactory.getLogger(
                "org.springframework.context.support.PostProcessorRegistrationDelegate$BeanPostProcessorChecker");
        ListAppender<ILoggingEvent> messages = new ListAppender<>();
        messages.start();
        checker.addAppender(messages);
        try {
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    CoreJacksonAutoConfiguration.class,
                                    JacksonAutoConfiguration.class,
                                    AppIsolationAutoConfiguration.class,
                                    CoreSpringAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.application.name=sca4d-test",
                            "sca4dj.i18n.supported-locales[0]=en-US",
                            "sca4dj.i18n.default-locale=en-US")
                    .withUserConfiguration(ProbeConfiguration.class)
                    .withBean(RateLimiter.class, () -> mock(RateLimiter.class))
                    .withBean(IdempotencyStore.class, () -> mock(IdempotencyStore.class))
                    .withBean(DistributedLock.class, () -> mock(DistributedLock.class))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(ProcessedBeans.class).names)
                                .contains(
                                        "recordingParticipant",
                                        "transactionProbe",
                                        "callInvoker",
                                        "callLifecycleConfiguration",
                                        "callPreCommitTransactionListener");
                        assertThat(AopUtils.isAopProxy(context.getBean(TransactionProbe.class))).isTrue();
                        assertThat(context.getBean(CallTarget.class).invoke()).isEqualTo("done");
                        assertThat(context.getBean(PlainCallTarget.class).invoke()).isEqualTo("plain");
                        assertThat(context.getBean(MethodBoundTarget.class).invoke()).isEqualTo("method-bound");
                        assertThat(context.getBean(ProbeTransactionManager.class).commits).isEqualTo(3);
                        assertThat(context.getBean(ProbeTransactionManager.class).getTransactionExecutionListeners())
                                .contains(context.getBean(CallPreCommitTransactionListener.class));
                        assertThat(messages.list).extracting(ILoggingEvent::getFormattedMessage).isEmpty();
                    });
        } finally {
            checker.detachAppender(messages);
            messages.stop();
        }
    }

    /** 同时注册提前读取 Advisor 的后处理器和稍后安装的观察后处理器。 */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static class ProbeConfiguration {

        /** 模拟后处理器创建期间由自动代理发现 Advisor。 */
        @Bean
        static EarlyAdvisorReader earlyAdvisorReader(CallLifecycleAdvisor advisor) {
            return new EarlyAdvisorReader(advisor);
        }

        /** 注册期间创建的 Bean 没有机会经过这个后处理器。 */
        @Bean
        static BeanPostProcessor processedBeanObserver(ProcessedBeans processed) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    processed.names.add(name);
                    return bean;
                }
            };
        }

        /** 保存后处理结果，基础设施观察器不参与代理。 */
        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        static ProcessedBeans processedBeans() {
            return new ProcessedBeans();
        }

        /** 提供不访问数据库的真实 Spring 事务生命周期。 */
        @Bean
        ProbeTransactionManager transactionManager() {
            return new ProbeTransactionManager();
        }

        /** 必须被 Spring 事务代理处理的运行依赖。 */
        @Bean
        TransactionProbe transactionProbe() {
            return new TransactionProbe();
        }

        /** 模拟 Audit 等参与者对事务记录器的依赖。 */
        @Bean
        CallLifecycle.Around recordingParticipant(TransactionProbe recorder) {
            return new CallLifecycle.Around() {
                @Override
                public @NotNull TreeCode code() {
                    return TreeCode.of("TEST/RECORDING");
                }

                @Override
                public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
                    CallOutcome outcome = next.proceed();
                    recorder.record();
                    return outcome;
                }
            };
        }

        /** 提供可通过 Call 代理调用的目标。 */
        @Bean
        CallTarget callTarget() {
            return new CallTarget();
        }

        /** 注册没有 `CallLifecycle.Intercepted` 元标记的应用注解绑定。 */
        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        CallLifecycleAdvisor.AnnotationBinding<PlainCall> plainCallBinding() {
            return () -> PlainCall.class;
        }

        /** 注册不依赖业务注解的方法绑定。 */
        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        CallLifecycleAdvisor.MethodBinding methodBinding() {
            return (method, targetType) -> targetType == MethodBoundTarget.class
                    && method.getName().equals("invoke");
        }

        @Bean
        PlainCallTarget plainCallTarget() {
            return new PlainCallTarget();
        }

        @Bean
        MethodBoundTarget methodBoundTarget() {
            return new MethodBoundTarget();
        }
    }

    /** 在其他普通后处理器安装前取得 Advisor。 */
    static class EarlyAdvisorReader implements BeanPostProcessor, Ordered {
        EarlyAdvisorReader(CallLifecycleAdvisor advisor) {
            assertThat(advisor.getPointcut()).isNotNull();
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    /** 保存真正经过普通后处理器的 Bean 名称。 */
    static class ProcessedBeans {
        private final List<String> names = new ArrayList<>();
    }

    /** 检查参与者读取到的是事务代理后的对象。 */
    public static class TransactionProbe {
        /** 写入前必须已经开始事务。 */
        @Transactional
        public void record() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        }
    }

    /** 声明匿名方法，使测试不依赖 Session 初始化。 */
    public static class CallTarget {
        /** 返回业务结果，由外层参与者记录。 */
        @AnonymousAccess
        public String invoke() {
            return "done";
        }
    }

    /** 没有 CallLifecycle.Intercepted 元标记的注解目标。 */
    @AnonymousAccess
    static class PlainCallTarget {
        @PlainCall
        String invoke() {
            return "plain";
        }
    }

    /** 完全没有业务注解的方法绑定目标。 */
    @AnonymousAccess
    static class MethodBoundTarget {
        String invoke() {
            return "method-bound";
        }
    }

    /** 只用于验证注册绑定不要求业务注解携带 CallLifecycle.Intercepted。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface PlainCall {
    }

    /** 保留真实 Spring 同步回调，统计实际提交次数。 */
    static class ProbeTransactionManager extends AbstractPlatformTransactionManager {
        private int commits;

        @Override
        protected @NotNull Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(@NotNull Object transaction, @NotNull TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(@NotNull DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(@NotNull DefaultTransactionStatus status) {
        }
    }
}
