package com.sca4d.audit.config;

import com.sca4d.audit.payload.AuditPayloadJson;
import com.sca4d.audit.payload.AuditPayloadRegistry;
import com.sca4d.audit.query.AuditQueryService;
import com.sca4d.audit.recording.AuditRepository;
import com.sca4d.audit.recording.DatabaseAuditRecorder;
import com.sca4d.audit.recording.mapper.AuditMapper;
import com.sca4d.core.api.audit.AuditPayloadCodec;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.spring.config.CoreSpringAutoConfiguration;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.List;

/** 自动装配通用审计编码、数据库记录器和查询服务。 */
@AutoConfiguration(before = CoreSpringAutoConfiguration.class)
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
    @ConditionalOnMissingBean(AuditRecorder.class)
    AuditRecorder databaseAuditRecorder(
            @NotNull AuditRepository repository) {
        return new DatabaseAuditRecorder(repository);
    }

    @Bean
    AuditQueryService auditQueryService(
            @NotNull AuditRepository repository) {
        return new AuditQueryService(repository);
    }
}
