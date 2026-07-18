package com.sca4d.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.audit.AuditAspect;
import com.sca4d.common.audit.AuditCodec;
import com.sca4d.common.audit.AuditMode;
import com.sca4d.common.audit.AuditPayloadCodec;
import com.sca4d.common.audit.AuditPayloadRegistry;
import com.sca4d.common.audit.AuditRecorder;
import com.sca4d.common.audit.AuditSource;
import com.sca4d.common.audit.DefaultAuditSource;
import com.sca4d.common.audit.LoggingAuditRecorder;
import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.resource.ResourceReferenceResolver;
import com.sca4d.config.properties.Sca4djAuditProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/** 装配审计声明解析、来源采集、版本化载荷和可替换记录器。 */
@Configuration
@EnableConfigurationProperties(Sca4djAuditProperties.class)
public class AuditConfig {

    /** 审计异步执行器 Bean 名称。 */
    public static final String AUDIT_EXECUTOR = "auditExecutor";

    /**
     * 汇总所有业务审计载荷 Codec 并在启动阶段拒绝重复类型、版本和 Java 类。
     *
     * @param codecs 当前应用上下文中的业务与公共组件 Codec
     * @return 完成无歧义校验的不可变注册关系
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull AuditPayloadRegistry auditPayloadRegistry(@NotNull List<AuditPayloadCodec<?>> codecs) {
        return new AuditPayloadRegistry(codecs);
    }

    /**
     * 创建共享全局 JSON 契约的审计详情编解码入口。
     *
     * @param objectMapper 已注册项目枚举和时间契约的全局映射器
     * @param registry 完成类型版本校验的载荷注册表
     * @param properties 详情长度和敏感字段配置
     * @return 供记录器和操作日志读取服务共同使用的编解码入口
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull AuditCodec auditCodec(
            @NotNull ObjectMapper objectMapper,
            @NotNull AuditPayloadRegistry registry,
            @NotNull Sca4djAuditProperties properties) {
        return new AuditCodec(objectMapper, registry, properties);
    }

    /**
     * 创建从 CurrentRequest 采集审计数据的默认来源。
     *
     * @param resourceReferenceResolver 按当前请求权限查询资源业务名称的解析器
     * @return 同时支持认证请求、匿名请求和无请求系统调用的来源采集器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull AuditSource auditSource(@NotNull ResourceReferenceResolver resourceReferenceResolver) {
        return new DefaultAuditSource(resourceReferenceResolver);
    }

    /**
     * 注册没有数据库审计实现时使用的日志记录器。
     *
     * @return 只写入最小索引数据且不记录载荷的日志记录器
     */
    @Bean
    @ConditionalOnMissingBean(AuditRecorder.class)
    public @NotNull AuditRecorder auditRecorder() {
        return new LoggingAuditRecorder();
    }

    /**
     * 创建不会无限积压且支持优雅关闭的审计执行器。
     *
     * @param properties 审计执行配置
     * @return 审计专用有界执行器
     */
    @Bean(name = AUDIT_EXECUTOR)
    @ConditionalOnMissingBean(name = AUDIT_EXECUTOR)
    public @NotNull ThreadPoolTaskExecutor auditExecutor(@NotNull Sca4djAuditProperties properties) {
        Sca4djAuditProperties.Async async = properties.getAsync();
        if (async.getWorkerCount() <= 0) {
            throw new IllegalStateException("审计异步工作线程数必须大于 0");
        }
        if (async.getQueueCapacity() <= 0) {
            throw new IllegalStateException("审计异步队列容量必须大于 0");
        }
        if (async.getShutdownAwait().isNegative()) {
            throw new IllegalStateException("审计关闭等待时间不能为负数");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getWorkerCount());
        executor.setMaxPoolSize(async.getWorkerCount());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("sca4dj-audit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(async.getShutdownAwait().toMillis());
        return executor;
    }

    /**
     * 创建声明式审计切面并绑定记录策略。
     *
     * @param recorder 项目持久化记录器或日志回退记录器
     * @param source 当前执行载体的审计数据来源
     * @param keyEvaluator 目标标识和摘要使用的共用 SpEL 解析器
     * @param registry 载荷运行时类型注册表
     * @param properties 成功失败记录和执行模式配置
     * @param auditExecutor 审计专用异步执行器
     * @param transactionManagers 当前应用事务管理器提供器
     * @param meterRegistry 审计拒绝和写入失败指标注册表
     * @return 按注解配置执行异步记录或同步事务记录的审计切面
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull AuditAspect auditAspect(
            @NotNull AuditRecorder recorder,
            @NotNull AuditSource source,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull AuditPayloadRegistry registry,
            @NotNull Sca4djAuditProperties properties,
            @Qualifier(AUDIT_EXECUTOR) @NotNull TaskExecutor auditExecutor,
            @NotNull ObjectProvider<PlatformTransactionManager> transactionManagers,
            @NotNull MeterRegistry meterRegistry) {
        if (properties.getMode() == AuditMode.DEFAULT) {
            throw new IllegalStateException("全局审计模式不能配置为 DEFAULT");
        }
        PlatformTransactionManager transactionManager = transactionManagers.getIfUnique();
        if (properties.getMode() == AuditMode.SYNC && transactionManager == null) {
            throw new IllegalStateException("同步审计模式需要唯一的事务管理器");
        }
        return new AuditAspect(
                recorder,
                source,
                keyEvaluator,
                registry,
                properties,
                auditExecutor,
                transactionManager,
                meterRegistry);
    }
}
