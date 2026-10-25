package com.sca4d.core.spring.call;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.transaction.ConfigurableTransactionManager;

import java.util.Objects;
import java.util.function.Supplier;

/** 把预提交条件监听器安装到应用提供的每一个可配置 Spring 事务管理器。 */
public class CallPreCommitTransactionInstaller implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CallPreCommitTransactionInstaller.class);

    private final Supplier<CallPreCommitTransactionListener> listenerSource;

    /** 创建预提交条件监听器安装器。 */
    public CallPreCommitTransactionInstaller(CallPreCommitTransactionListener listener) {
        this(() -> Objects.requireNonNull(listener, "预提交条件监听器不能为空"));
    }

    /** 使用延迟提供者，避免安装器注册时提前创建事务监听器。 */
    public CallPreCommitTransactionInstaller(ObjectProvider<CallPreCommitTransactionListener> listener) {
        this(listener::getObject);
    }

    private CallPreCommitTransactionInstaller(Supplier<CallPreCommitTransactionListener> listenerSource) {
        this.listenerSource = Objects.requireNonNull(listenerSource, "预提交条件监听器来源不能为空");
    }

    /**
     * 在事务管理器完成初始化后把监听器加入其执行监听器列表。
     *
     * <p>同一监听器已经存在时原样返回，不重复添加；非 `ConfigurableTransactionManager` 的 Bean 不做修改。
     */
    @Override
    public @NotNull Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) {
        if (bean instanceof ConfigurableTransactionManager transactionManager) {
            CallPreCommitTransactionListener listener = listenerSource.get();
            if (transactionManager.getTransactionExecutionListeners().contains(listener)) {
                return bean;
            }
            transactionManager.addListener(listener);
            LOG.trace(
                    "预提交条件监听器已安装到事务管理器 | beanName={} transactionManagerType={}",
                    beanName,
                    bean.getClass().getName());
        }
        return bean;
    }
}
