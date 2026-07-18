package com.sca4d.system.view;

import com.sca4d.common.audit.AuditEnvelope;
import com.sca4d.system.audit.OperationLog;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/**
 * 操作日志详情视图。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class OperationLogDetailView extends OperationLogView {

    private Object actor;
    private Object target;
    private Object request;
    private Object error;
    private Object payload;

    /** @return 包含操作者、目标、请求、错误和类型化审计内容的详情视图 */
    public static @NotNull OperationLogDetailView from(
            @NotNull OperationLog log, @NotNull AuditPayloadViewRegistry payloadViewRegistry) {
        OperationLogView base = OperationLogView.from(log);
        AuditEnvelope envelope = log.getAudit().getEnvelope();
        OperationLogDetailView view = new OperationLogDetailView();
        view.setId(base.getId());
        view.setSubjectId(base.getSubjectId());
        view.setAccountId(base.getAccountId());
        view.setPrincipalId(base.getPrincipalId());
        view.setSubjectDisplayName(base.getSubjectDisplayName());
        view.setPrincipalDisplayName(base.getPrincipalDisplayName());
        view.setModuleCode(base.getModuleCode());
        view.setModuleName(base.getModuleName());
        view.setActionCode(base.getActionCode());
        view.setActionName(base.getActionName());
        view.setResult(base.getResult());
        view.setRequestUri(base.getRequestUri());
        view.setPayloadType(base.getPayloadType());
        view.setSummary(base.getSummary());
        view.setActorReference(base.getActorReference());
        view.setTargetReference(base.getTargetReference());
        view.setCreatedAt(base.getCreatedAt());
        view.setActor(envelope.getActor());
        view.setTarget(envelope.getTarget());
        view.setRequest(envelope.getRequest());
        view.setError(envelope.getError());
        view.setPayload(payloadViewRegistry.toView(envelope.getPayloadType(), log.getAudit().getPayload()));
        return view;
    }
}
