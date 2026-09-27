package com.sca4d.core.spring.call;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.transaction.ConfigurableTransactionManager;

import java.util.Objects;

/** 把调用要求监听器安装到应用提供的每一个可配置 Spring 事务管理器。 */
public class CallRequirementTransactionInstaller implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CallRequirementTransactionInstaller.class);

    private final CallRequirementTransactionListener listener;

    /**
     * 创建事务监听器安装器。
     *
     * @param listener 事务提交前调用要求监听器
     */
    public CallRequirementTransactionInstaller(CallRequirementTransactionListener listener) {
        this.listener = Objects.requireNonNull(listener, "调用要求事务监听器不能为空");
    }

    /**
     * 在事务管理器完成初始化后安装监听器。
     *
     * @param bean 已初始化 Bean
     * @param beanName Spring Bean 名称
     * @return 原 Bean
     */
    @Override
    public @NotNull Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) {
        if (bean instanceof ConfigurableTransactionManager transactionManager
                && !transactionManager.getTransactionExecutionListeners().contains(listener)) {
            transactionManager.addListener(listener);
            LOG.trace(
                    "调用要求监听器已安装到事务管理器 | beanName={} transactionManagerType={}",
                    beanName,
                    bean.getClass().getName());
        }
        return bean;
    }
}
