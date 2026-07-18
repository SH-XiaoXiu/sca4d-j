package com.sca4d.system.view;

import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.resource.view.ResourceReferenceView;
import com.sca4d.system.audit.OperationLog;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/**
 * 操作日志列表视图。
 */
@Data
@Accessors(chain = true)
public class OperationLogView {

    private Long id;
    private Long subjectId;
    private Long accountId;
    private Long principalId;
    private String subjectDisplayName;
    private String principalDisplayName;
    private String moduleCode;
    private String moduleName;
    private String actionCode;
    private String actionName;
    private AuditResult result;
    private String requestUri;
    private String payloadType;
    private String summary;
    private ResourceReferenceView actorReference;
    private ResourceReferenceView targetReference;
    private OffsetDateTime createdAt;

    /** @return 不包含完整请求和审计内容的操作日志列表视图 */
    public static @NotNull OperationLogView from(@NotNull OperationLog log) {
        return new OperationLogView()
                .setId(log.getId())
                .setSubjectId(log.getSubjectId())
                .setAccountId(log.getAccountId())
                .setPrincipalId(log.getPrincipalId())
                .setSubjectDisplayName(log.getSubjectDisplayName())
                .setPrincipalDisplayName(log.getPrincipalDisplayName())
                .setModuleCode(log.getModuleCode())
                .setModuleName(log.getModuleName())
                .setActionCode(log.getActionCode())
                .setActionName(log.getActionName())
                .setResult(log.getResult())
                .setRequestUri(log.getRequestUri())
                .setPayloadType(log.getAudit().getEnvelope().getPayloadType())
                .setSummary(log.getSummary())
                .setActorReference(log.getActorReference() == null ? null : ResourceReferenceView.from(log.getActorReference()))
                .setTargetReference(log.getTargetReference() == null ? null : ResourceReferenceView.from(log.getTargetReference()))
                .setCreatedAt(log.getCreatedAt());
    }
}
