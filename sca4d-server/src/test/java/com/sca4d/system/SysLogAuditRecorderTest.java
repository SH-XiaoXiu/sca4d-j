package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.audit.AuditActor;
import com.sca4d.common.audit.AuditCodec;
import com.sca4d.common.audit.AuditOperationCatalog;
import com.sca4d.common.audit.AuditPayloadRegistry;
import com.sca4d.common.audit.AuditRecord;
import com.sca4d.common.audit.AuditRequest;
import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.audit.RawAuditPayload;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.config.properties.Sca4djAuditProperties;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.system.audit.SysLogAuditRecorder;
import com.sca4d.system.audit.SystemAuditOperations;
import com.sca4d.system.repository.OperationLogRepository;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 验证审计记录转换为操作日志实体并写入数据库。 */
class SysLogAuditRecorderTest {

    @Test
    void shouldWriteAuditRecordIntoOperationLogRepository() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        SysLogAuditRecorder recorder = new SysLogAuditRecorder(
                repository,
                auditCodec(),
                new AuditOperationCatalog(List.of(new SystemAuditOperations())),
                fixedIdGenerator());
        AuditActor actor = new AuditActor();
        actor.setSubjectId(1L);
        actor.setSubjectDisplayName("平台管理员");
        actor.setPrincipalId(10L);
        actor.setPrincipalDisplayName("平台主体");
        AuditRequest request = new AuditRequest();
        request.setPath("/mg/system/operation-logs");
        AuditRecord record = new AuditRecord();
        record.setActor(actor);
        record.setRequest(request);
        record.setModuleCode("system.dictionaries");
        record.setActionCode("create");
        record.setResult(AuditResult.SUCCESS);
        record.setCreatedAt(OffsetDateTime.parse("2026-07-05T10:00:00+08:00"));
        record.setPayload(new RawAuditPayload(Map.of("name", "demo")));

        recorder.record(record);

        ArgumentCaptor<SysOperationLogEntity> entityCaptor = ArgumentCaptor.forClass(SysOperationLogEntity.class);
        ArgumentCaptor<String> detailJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).appendAudit(entityCaptor.capture(), detailJsonCaptor.capture());
        SysOperationLogEntity entity = entityCaptor.getValue();
        assertThat(entity.getId()).isEqualTo(100L);
        assertThat(entity.getSubjectId()).isEqualTo(1L);
        assertThat(entity.getPrincipalId()).isEqualTo(10L);
        assertThat(entity.getSubjectDisplayName()).isEqualTo("平台管理员");
        assertThat(entity.getPrincipalDisplayName()).isEqualTo("平台主体");
        assertThat(entity.getModuleCode()).isEqualTo("system.dictionaries");
        assertThat(entity.getActionCode()).isEqualTo("create");
        assertThat(entity.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(entity.getRequestUri()).isEqualTo("/mg/system/operation-logs");
        assertThat(detailJsonCaptor.getValue()).contains("\"payloadType\":\"raw\"");
        assertThat(detailJsonCaptor.getValue())
                .contains("\"summaryKey\":\"audit.summary.system.dictionaries.create\"");
        assertThat(detailJsonCaptor.getValue()).contains("\"name\":\"demo\"");
    }

    /**
     * 创建不注册业务载荷编解码器的审计 JSON 夹具。
     *
     * @return 使用真实审计信封格式的编解码器
     */
    private static @NotNull AuditCodec auditCodec() {
        return new AuditCodec(new ObjectMapper(), new AuditPayloadRegistry(List.of()), new Sca4djAuditProperties());
    }

    /**
     * 创建始终返回固定主键的测试 ID 生成器。
     *
     * @return 每次生成 {@code 100} 的 ID 生成器
     */
    private static @NotNull IdGenerator fixedIdGenerator() {
        return () -> 100L;
    }
}
