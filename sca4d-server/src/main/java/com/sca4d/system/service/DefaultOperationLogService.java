package com.sca4d.system.service;

import com.sca4d.common.audit.AuditCodec;
import com.sca4d.common.audit.AuditDecodedEnvelope;
import com.sca4d.common.audit.AuditEnvelope;
import com.sca4d.common.audit.AuditOperationCatalog;
import com.sca4d.common.audit.AuditOperationDefinition;
import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.common.resource.ResourceReferenceResolver;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.system.OperationLogService;
import com.sca4d.system.SystemErrorCode;
import com.sca4d.system.audit.OperationLog;
import com.sca4d.system.audit.OperationLogActionDescription;
import com.sca4d.system.audit.OperationLogModuleDescription;
import com.sca4d.system.audit.OperationLogOperatorOption;
import com.sca4d.system.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志读取与审计载荷解码实现。
 */
@Service
@RequiredArgsConstructor
public class DefaultOperationLogService implements OperationLogService {

    private final OperationLogRepository repository;
    private final AuditCodec auditCodec;
    private final AuditOperationCatalog auditOperationCatalog;
    private final LocalizedMessages localizedMessages;
    private final ResourceReferenceResolver resourceReferenceResolver;

    @Override
    public @NotNull PageData<OperationLogOperatorOption> pageOperatorOptions(
            @Nullable String search, long page, long size) {
        return repository.pageOperatorOptions(search, page, size);
    }

    @Override
    public @NotNull List<OperationLogModuleDescription> catalog() {
        Map<String, MutableModule> modules = new LinkedHashMap<>();
        for (AuditOperationDefinition operation : auditOperationCatalog.operations()) {
            MutableModule module = modules.computeIfAbsent(
                    operation.getModuleCode(),
                    ignored -> new MutableModule(localizedMessages.get(operation.getModuleNameKey())));
            module.actions.add(new OperationLogActionDescription(
                    operation.getActionCode(), localizedMessages.get(operation.getActionNameKey())));
        }
        return modules.entrySet().stream()
                .map(entry -> new OperationLogModuleDescription(
                        entry.getKey(), entry.getValue().name, entry.getValue().actions))
                .toList();
    }

    @Override
    public @NotNull PageData<OperationLog> page(
            @Nullable String moduleCode,
            @Nullable String actionCode,
            @Nullable AuditResult result,
            @Nullable Long subjectId,
            @Nullable Long principalId,
            @Nullable String targetType,
            @Nullable String targetId,
            @Nullable String traceId,
            @Nullable OffsetDateTime createdAtStart,
            @Nullable OffsetDateTime createdAtEnd,
            long page,
            long size) {
        // Repository 返回数据库日志记录；Service 在分页返回前解码审计 JSON，Controller 不需要读取存储格式。
        PageData<SysOperationLogEntity> records = repository.page(
                moduleCode,
                actionCode,
                result,
                subjectId,
                principalId,
                targetType,
                targetId,
                traceId,
                createdAtStart,
                createdAtEnd,
                page,
                size);
        Map<ResourceReferenceKey, ResourceReference> references = resolveReferences(records.getRecords());
        return records.map(entity -> toOperationLog(entity, references));
    }

    @Override
    public @NotNull OperationLog detail(@NotNull Long id) {
        // 详情与分页使用同一个审计 JSON 解码方法；找不到记录时返回系统模块的未找到错误。
        return repository.findById(id)
                .map(entity -> toOperationLog(entity, resolveReferences(List.of(entity))))
                .orElseThrow(() -> new BizException(SystemErrorCode.AUDIT_LOG_NOT_FOUND));
    }

    /**
     * 解码日志审计 JSON，并补充本地化名称、摘要以及操作者和目标资源引用。
     *
     * @param entity 数据库操作日志记录
     * @param references 当前页已经批量查询的资源引用
     * @return 管理端查询使用的操作日志对象
     */
    private @NotNull OperationLog toOperationLog(
            @NotNull SysOperationLogEntity entity,
            @NotNull Map<ResourceReferenceKey, ResourceReference> references) {
        AuditOperationDefinition operation = auditOperationCatalog.require(
                entity.getModuleCode(), entity.getActionCode());
        AuditDecodedEnvelope decoded = auditCodec.decode(entity.getDetailJson());
        AuditEnvelope envelope = decoded.getEnvelope();
        String summaryKey = envelope.getSummaryKey() == null
                ? operation.getSummaryKey()
                : envelope.getSummaryKey();
        return new OperationLog(
                entity.getId(),
                entity.getSubjectId(),
                entity.getAccountId(),
                entity.getPrincipalId(),
                decoded,
                entity.getModuleCode(),
                localizedMessages.get(operation.getModuleNameKey()),
                entity.getActionCode(),
                localizedMessages.get(operation.getActionNameKey()),
                entity.getResult(),
                entity.getRequestUri(),
                localizedMessages.get(summaryKey, envelope.getSummaryArgs().toArray()),
                actorReference(entity, envelope, references),
                targetReference(entity, envelope, references),
                entity.getCreatedAt());
    }

    /**
     * 批量解析一页日志涉及的操作者和目标资源。
     *
     * <p>先在内存去重，再由业务模块贡献者按实际出现类型各执行至多一条查询；总查询数与日志记录数无关。
     *
     * @param entities 当前日志页或单条详情记录
     * @return 资源类型和标识到当前请求可见引用的映射
     */
    private @NotNull Map<ResourceReferenceKey, ResourceReference> resolveReferences(
            @NotNull List<SysOperationLogEntity> entities) {
        Set<ResourceReferenceKey> keys = new LinkedHashSet<>();
        for (SysOperationLogEntity entity : entities) {
            if (entity.getSubjectId() != null) {
                keys.add(new ResourceReferenceKey(IamResourceTypes.SUBJECT, String.valueOf(entity.getSubjectId())));
            }
            if (entity.getTargetType() != null && !entity.getTargetType().isBlank()
                    && entity.getTargetId() != null && !entity.getTargetId().isBlank()) {
                keys.add(new ResourceReferenceKey(entity.getTargetType(), entity.getTargetId()));
            }
        }
        return resourceReferenceResolver.resolve(keys);
    }

    /** 已删除操作者优先使用审计信封中的 Subject 名称快照，避免名称与删除状态重复。 */
    private @Nullable ResourceReference actorReference(
            @NotNull SysOperationLogEntity entity,
            @NotNull AuditEnvelope envelope,
            @NotNull Map<ResourceReferenceKey, ResourceReference> references) {
        ResourceReference current = entity.getSubjectId() == null
                ? null
                : references.get(new ResourceReferenceKey(IamResourceTypes.SUBJECT, String.valueOf(entity.getSubjectId())));
        if (current == null || current.getNavigationState() != ResourceNavigationState.DELETED
                || envelope.getActor() == null || envelope.getActor().getSubjectDisplayName() == null
                || envelope.getActor().getSubjectDisplayName().isBlank()) {
            return current;
        }
        return new ResourceReference(
                current.getKey(),
                envelope.getActor().getSubjectDisplayName(),
                current.getTenantId(),
                ResourceNavigationState.DELETED,
                null,
                current.getNavigationMessage());
    }

    /** 已删除目标优先显示审计信封保存的业务名称；旧信封没有快照时保留共用删除态。 */
    private @Nullable ResourceReference targetReference(
            @NotNull SysOperationLogEntity entity,
            @NotNull AuditEnvelope envelope,
            @NotNull Map<ResourceReferenceKey, ResourceReference> references) {
        ResourceReference current = entity.getTargetType() == null || entity.getTargetType().isBlank()
                || entity.getTargetId() == null || entity.getTargetId().isBlank()
                        ? null
                        : references.get(new ResourceReferenceKey(entity.getTargetType(), entity.getTargetId()));
        if (current == null || current.getNavigationState() != ResourceNavigationState.DELETED
                || envelope.getTarget() == null || envelope.getTarget().getLabel() == null
                || envelope.getTarget().getLabel().isBlank()) {
            return current;
        }
        return new ResourceReference(
                current.getKey(),
                envelope.getTarget().getLabel(),
                current.getTenantId(),
                ResourceNavigationState.DELETED,
                null,
                current.getNavigationMessage());
    }

    /** 在目录构建期间累计同一模块的审计动作，构建完成后转换为不可变结果。 */
    private static final class MutableModule {

        private final String name;
        private final List<OperationLogActionDescription> actions = new ArrayList<>();

        private MutableModule(@NotNull String name) {
            this.name = name;
        }
    }
}
