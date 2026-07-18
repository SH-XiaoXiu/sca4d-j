package com.sca4d.system.audit;

import com.sca4d.common.audit.AuditCodec;
import com.sca4d.common.audit.AuditOperationCatalog;
import com.sca4d.common.audit.AuditOperationDefinition;
import com.sca4d.common.audit.AuditRecord;
import com.sca4d.common.audit.AuditRecorder;
import com.sca4d.common.i18n.MessageReference;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.system.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 系统操作日志审计落库实现。
 */
@Component
@RequiredArgsConstructor
public class SysLogAuditRecorder implements AuditRecorder {

    private final OperationLogRepository repository;
    private final AuditCodec auditCodec;
    private final AuditOperationCatalog operationCatalog;
    private final IdGenerator idGenerator;

    /**
     * 持久化审计记录，事务传播由审计执行模式共用控制。
     *
     * @param record 已完整采集并编码的审计数据
     */
    @Override
    public void record(@NotNull AuditRecord record) {
        String moduleCode = Objects.requireNonNull(record.getModuleCode(), "审计记录缺少模块编码");
        String actionCode = Objects.requireNonNull(record.getActionCode(), "审计记录缺少动作编码");
        AuditOperationDefinition operation = operationCatalog.require(moduleCode, actionCode);
        if (record.getSummary() == null) {
            record.setSummary(MessageReference.of(operation.getSummaryKey()));
        }
        SysOperationLogEntity entity = new SysOperationLogEntity()
                .setId(record.getId() == null ? idGenerator.nextId() : record.getId())
                .setSubjectId(record.getActor() == null ? null : record.getActor().getSubjectId())
                .setAccountId(record.getActor() == null ? null : record.getActor().getAccountId())
                .setPrincipalId(record.getActor() == null ? null : record.getActor().getPrincipalId())
                .setSubjectDisplayName(
                        record.getActor() == null ? null : record.getActor().getSubjectDisplayName())
                .setPrincipalDisplayName(
                        record.getActor() == null ? null : record.getActor().getPrincipalDisplayName())
                .setModuleCode(moduleCode)
                .setActionCode(actionCode)
                .setResult(record.getResult())
                .setRequestUri(record.getRequest() == null ? null : record.getRequest().getPath())
                .setTargetType(record.getTarget() == null ? null : record.getTarget().getType())
                .setTargetId(record.getTarget() == null ? null : record.getTarget().getId())
                .setCreatedAt(record.getCreatedAt());
        repository.appendAudit(entity, auditCodec.encode(record));
    }
}
