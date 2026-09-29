package com.sca4d.core.spring.call;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.transaction.ConfigurableTransactionManager;

import java.util.Objects;

/** 把预提交条件监听器安装到应用提供的每一个可配置 Spring 事务管理器。 */
public class CallPreCommitTransactionInstaller implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CallPreCommitTransactionInstaller.class);

    private final CallPreCommitTransactionListener listener;

    /** 创建预提交条件监听器安装器。 */
    public CallPreCommitTransactionInstaller(CallPreCommitTransactionListener listener) {
        this.listener = Objects.requireNonNull(listener, "预提交条件监听器不能为空");
    }

    /** 在事务管理器完成初始化后安装监听器。 */
    @Override
    public @NotNull Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) {
        if (bean instanceof ConfigurableTransactionManager transactionManager
                && !transactionManager.getTransactionExecutionListeners().contains(listener)) {
            transactionManager.addListener(listener);
            LOG.trace(
                    "预提交条件监听器已安装到事务管理器 | beanName={} transactionManagerType={}",
                    beanName,
                    bean.getClass().getName());
        }
        return bean;
    }
}
