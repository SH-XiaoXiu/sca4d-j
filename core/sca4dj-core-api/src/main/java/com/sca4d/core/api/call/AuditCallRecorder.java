package com.sca4d.core.api.call;

import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.audit.AuditActor;
import com.sca4d.core.api.audit.AuditInvocation;
import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.audit.AuditResolverLookup;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.audit.NoAuditPayloadFactory;
import com.sca4d.core.api.audit.NoAuditTargetResolver;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.resource.ResourceReference;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.util.Objects;

/** 把 `@Audit`、调用身份和 `CallOutcome` 转换成同步审计记录。 */
public final class AuditCallRecorder {

    private final AuditRecorder auditRecorder;

    private final AuditResolverLookup resolverLookup;

    private final Clock clock;

    /**
     * 创建调用结果记录器。
     *
     * @param auditRecorder 审计记录存储入口
     * @param resolverLookup 审计目标解析器和 payload 工厂查找入口
     * @param clock 审计发生时间使用的时钟
     */
    public AuditCallRecorder(
            AuditRecorder auditRecorder,
            AuditResolverLookup resolverLookup,
            Clock clock) {
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "审计记录器不能为空");
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "审计解析器目录不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 按当前方法的审计声明记录最终结果。
     *
     * @param execution 已经写入最终结果的方法执行
     */
    public void record(CallExecution execution) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Audit audit = checked.audit().orElse(null);
        if (audit == null) {
            return;
        }
        CallOutcome outcome = checked.outcome()
                .orElseThrow(() -> new IllegalStateException("记录审计前必须先写入方法执行结果"));
        if ((outcome.status() == CallOutcome.Status.DENIED || outcome.status() == CallOutcome.Status.FAILED)
                && (checked.call().subject().isEmpty() || checked.call().principal().isEmpty())) {
            return;
        }
        auditRecorder.record(auditRecord(audit, checked, auditResult(outcome)));
    }

    /**
     * 根据调用结果建立完整审计记录。
     *
     * @param audit 当前方法的审计声明
     * @param execution 当前方法执行
     * @param result 审计结果分类
     * @return 完整审计记录
     */
    private AuditRecord auditRecord(
            Audit audit,
            CallExecution execution,
            AuditResult result) {
        AuditInvocation invocation = new AuditInvocation(execution, result);
        Resource target = resolveTarget(audit, invocation);
        AuditPayload payload = audit.payloadFactory() == NoAuditPayloadFactory.class
                ? null
                : resolverLookup.payloadFactory(audit.payloadFactory()).create(invocation);
        AuditActor actor = new AuditActor(
                execution.call().subject().orElseThrow().subjectId(),
                execution.call().account().map(Account::accountId).orElse(null),
                execution.call().principal().orElseThrow().principalId());
        return new AuditRecord(
                execution.call().callId(),
                actor,
                AuditOperation.declared(audit.operation()),
                target,
                result,
                payload,
                execution.call().httpCall().orElse(null),
                clock.instant());
    }

    /**
     * 把方法执行结果转换成审计结果分类。
     *
     * @param outcome 当前方法执行结果
     * @return 审计结果分类
     */
    private static AuditResult auditResult(CallOutcome outcome) {
        return switch (outcome.status()) {
            case SUCCESS, REPLAYED -> AuditResult.SUCCESS;
            case DENIED -> AuditResult.DENIED;
            case FAILED -> AuditResult.FAILED;
        };
    }

    /**
     * 按参数标记或复杂解析器取得审计目标。
     *
     * @param audit 当前审计声明
     * @param invocation 完整审计调用
     * @return 可选目标资源
     */
    private Resource resolveTarget(Audit audit, AuditInvocation invocation) {
        AuditTargetResolver resolver = audit.targetResolver() == NoAuditTargetResolver.class
                ? null
                : resolverLookup.targetResolver(audit.targetResolver());
        AuditTarget markedTarget = null;
        int markedIndex = -1;
        Annotation[][] parameterAnnotations = invocation.method().getParameterAnnotations();
        for (int index = 0; index < parameterAnnotations.length; index++) {
            for (Annotation annotation : parameterAnnotations[index]) {
                if (annotation instanceof AuditTarget target) {
                    if (markedTarget != null) {
                        throw new IllegalStateException("一次 Audit 只能标记一个目标参数");
                    }
                    markedTarget = target;
                    markedIndex = index;
                }
            }
        }
        if (resolver != null && markedTarget != null) {
            throw new IllegalStateException("Audit 不能同时使用 targetResolver 和 @AuditTarget 参数");
        }
        if (resolver != null) {
            return resolver.resolve(invocation);
        }
        if (markedTarget == null) {
            return null;
        }
        Object value = invocation.arguments()[markedIndex];
        if (value instanceof Resource resource) {
            return resource;
        }
        String id;
        if (value instanceof Number number) {
            if (number.longValue() <= 0) {
                throw new IllegalStateException("Audit 目标标识必须为正数");
            }
            id = Long.toString(number.longValue());
        } else if (value instanceof String text && !text.isBlank()) {
            id = text.trim();
        } else {
            throw new IllegalStateException("Audit 目标参数必须是 Resource、正数或非空文本");
        }
        return new ResourceReference(markedTarget.resourceType(), markedTarget.idPrefix() + id);
    }

}
