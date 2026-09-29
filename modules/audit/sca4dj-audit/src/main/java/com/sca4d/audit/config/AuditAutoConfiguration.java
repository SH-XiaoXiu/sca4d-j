package com.sca4d.audit.config;

import com.sca4d.audit.payload.AuditPayloadJson;
import com.sca4d.audit.payload.AuditPayloadRegistry;
import com.sca4d.audit.query.AuditQueryService;
import com.sca4d.audit.recording.AuditMessageCodec;
import com.sca4d.audit.recording.AuditMessageHandler;
import com.sca4d.audit.recording.AuditRepository;
import com.sca4d.audit.recording.DatabaseAuditRecorder;
import com.sca4d.audit.recording.QueuedAuditRecorder;
import com.sca4d.audit.recording.SpringAuditResolverLookup;
import com.sca4d.audit.recording.mapper.AuditMapper;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.audit.AuditCallInterceptor;
import com.sca4d.core.api.audit.AuditPayloadCodec;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.audit.AuditResolverLookup;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.spring.call.CallLifecycleAdvisor;
import com.sca4d.core.spring.config.CoreSpringAutoConfiguration;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 自动装配通用审计编码、数据库记录器和查询服务。 */
@AutoConfiguration(before = CoreSpringAutoConfiguration.class, afterName = "com.sca4d.messaging.config.MessagingAutoConfiguration")
@MapperScan(basePackageClasses = AuditMapper.class)
@EnableConfigurationProperties(AuditProperties.class)
@EnableTransactionManagement
public class AuditAutoConfiguration {
    /** 创建 Audit 模块自动配置。 */
    public AuditAutoConfiguration() {
    }

    @Bean
    AuditPayloadRegistry auditPayloadRegistry(
            @NotNull List<@NotNull AuditPayloadCodec<?>> codecs) {
        return new AuditPayloadRegistry(codecs);
    }

    @Bean
    AuditPayloadJson auditPayloadJson(
            @NotNull @Sca4djJson Sca4djMapper objectMapper,
            @NotNull AuditPayloadRegistry registry,
            @NotNull AuditProperties properties) {
        return new AuditPayloadJson(objectMapper, registry, properties);
    }

    @Bean
    AuditRepository auditRepository(
            @NotNull AuditMapper mapper,
            @NotNull IdGenerator ids,
            @NotNull AuditPayloadJson payloadJson,
            @NotNull InstanceProperties instanceProperties) {
        return new AuditRepository(mapper, ids, payloadJson, instanceProperties.getId());
    }

    @Bean
    @ConditionalOnMissingBean(name = "databaseAuditRecorder")
    AuditRecorder databaseAuditRecorder(
            @NotNull AuditRepository repository) {
        return new DatabaseAuditRecorder(repository);
    }

    /** Messaging 存在时安装队列记录器，供全局默认及方法覆盖选择。 */
    @Bean
    @ConditionalOnMissingBean(name = "queuedAuditRecorder")
    @ConditionalOnBean(MessageBus.class)
    AuditRecorder queuedAuditRecorder(AuditRepository repository, MessageBus messages) {
        return new QueuedAuditRecorder(repository, messages);
    }

    /** 即使默认同步记录，也为局部队列声明安装编码器。 */
    @Bean
    @ConditionalOnBean(MessageBus.class)
    AuditMessageCodec auditMessageCodec(@Sca4djJson Sca4djMapper json) {
        return new AuditMessageCodec(json);
    }

    /** 消费者只负责冻结记录落库，不依赖发起者 Call。 */
    @Bean
    @ConditionalOnBean(MessageBus.class)
    AuditMessageHandler auditMessageHandler(AuditRepository repository, AuditMessageCodec codec) {
        return new AuditMessageHandler(repository, codec);
    }

    /** Audit 自己注册调用声明，Core Spring 不维护审计注解清单。 */
    @Bean
    CallLifecycleAdvisor.Declaration<Audit> auditCallDeclaration() {
        return () -> Audit.class;
    }

    /** 从当前 Spring 容器读取审计目标解析器和 payload 工厂。 */
    @Bean
    @ConditionalOnMissingBean
    AuditResolverLookup auditResolverLookup(ApplicationContext applicationContext) {
        return new SpringAuditResolverLookup(applicationContext);
    }

    /** 优先选择每种方式唯一的自定义实现，默认实现只补齐尚未提供的方式。 */
    @Bean
    AuditCallInterceptor auditCallInterceptor(
            List<AuditRecorder> recorders,
            @Qualifier("databaseAuditRecorder") ObjectProvider<AuditRecorder> database,
            @Qualifier("queuedAuditRecorder") ObjectProvider<AuditRecorder> queued,
            AuditProperties properties,
            AuditResolverLookup resolvers,
            Clock clock) {
        AuditRecorder directDefault = database.getIfAvailable();
        AuditRecorder queuedDefault = queued.getIfAvailable();
        Map<Audit.Mode, AuditRecorder> selected = new EnumMap<>(Audit.Mode.class);
        for (AuditRecorder recorder : recorders) {
            if (recorder != directDefault
                    && recorder != queuedDefault
                    && selected.putIfAbsent(recorder.mode(), recorder) != null) {
                throw new IllegalArgumentException("同一审计方式不能注册多个自定义实现: " + recorder.mode());
            }
        }
        if (directDefault != null) {
            selected.putIfAbsent(directDefault.mode(), directDefault);
        }
        if (queuedDefault != null) {
            selected.putIfAbsent(queuedDefault.mode(), queuedDefault);
        }
        return new AuditCallInterceptor(List.copyOf(selected.values()), properties.getMode(), resolvers, clock);
    }

    @Bean
    AuditQueryService auditQueryService(
            @NotNull AuditRepository repository) {
        return new AuditQueryService(repository);
    }
}
