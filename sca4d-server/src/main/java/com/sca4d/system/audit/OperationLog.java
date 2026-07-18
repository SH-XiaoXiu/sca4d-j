package com.sca4d.system.audit;

import com.sca4d.common.audit.AuditDecodedEnvelope;
import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.resource.ResourceReference;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/**
 * 操作日志业务对象。
 */
@Getter
public class OperationLog {

    private final Long id;
    private final Long subjectId;
    private final Long accountId;
    private final Long principalId;
    private final AuditDecodedEnvelope audit;
    private final String moduleCode;
    private final String moduleName;
    private final String actionCode;
    private final String actionName;
    private final AuditResult result;
    private final String requestUri;
    private final String summary;
    private final ResourceReference actorReference;
    private final ResourceReference targetReference;
    private final OffsetDateTime createdAt;

    /**
     * 创建已经完成审计解码和本地化的读取对象。
     *
     * @param id 操作日志主键
     * @param subjectId 操作发生时的 Subject 主键
     * @param accountId 操作发生时的 Account 主键
     * @param principalId 操作发生时的 Principal 主键
     * @param audit 已解码版本化审计信封
     * @param moduleCode 模块编码
     * @param moduleName 当前请求语言下的模块名称
     * @param actionCode 动作编码
     * @param actionName 当前请求语言下的动作名称
     * @param result 操作结果
     * @param requestUri 请求路径
     * @param summary 当前请求语言下的操作摘要
     * @param actorReference 操作者用户资源引用；匿名或系统操作时为 null
     * @param targetReference 操作目标资源引用；没有唯一目标时为 null
     * @param createdAt 操作发生时间
     */
    public OperationLog(
            @NotNull Long id,
            @Nullable Long subjectId,
            @Nullable Long accountId,
            @Nullable Long principalId,
            @NotNull AuditDecodedEnvelope audit,
            @NotNull String moduleCode,
            @NotNull String moduleName,
            @NotNull String actionCode,
            @NotNull String actionName,
            @NotNull AuditResult result,
            @NotNull String requestUri,
            @NotNull String summary,
            @Nullable ResourceReference actorReference,
            @Nullable ResourceReference targetReference,
            @NotNull OffsetDateTime createdAt) {
        this.id = id;
        this.subjectId = subjectId;
        this.accountId = accountId;
        this.principalId = principalId;
        this.audit = audit;
        this.moduleCode = moduleCode;
        this.moduleName = moduleName;
        this.actionCode = actionCode;
        this.actionName = actionName;
        this.result = result;
        this.requestUri = requestUri;
        this.summary = summary;
        this.actorReference = actorReference;
        this.targetReference = targetReference;
        this.createdAt = createdAt;
    }

    /** @return 操作发生时保存的 Subject 展示名称快照 */
    public @Nullable String getSubjectDisplayName() {
        return audit.getEnvelope().getActor() == null
                ? null
                : audit.getEnvelope().getActor().getSubjectDisplayName();
    }

    /** @return 操作发生时保存的 Principal 展示名称快照 */
    public @Nullable String getPrincipalDisplayName() {
        return audit.getEnvelope().getActor() == null
                ? null
                : audit.getEnvelope().getActor().getPrincipalDisplayName();
    }
}
