package com.sca4d.core.api.audit;

import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.resource.ResourceReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** 根据有效 `@Audit` 和调用结果建立审计记录，选择同步写入或可靠排队方式。 */
public class AuditCallInterceptor implements CallLifecycle.Around {
    private static final Logger LOG = LoggerFactory.getLogger(AuditCallInterceptor.class);

    /** 审计环绕节点编码。 */
    public static final TreeCode CODE = TreeCode.of("CALL/AUDIT");

    private final Map<Audit.Mode, AuditRecorder> recorders;

    private final AuditResolverLookup resolverLookup;

    private final Clock clock;

    private final Audit.Mode defaultMode;

    private final SPICallValueResolver expressionResolver;

    /**
     * 安装每种方式唯一的记录器，校验应用默认方式存在。
     *
     * @param recorders 可用的同步写入和可靠排队实现
     * @param defaultMode 应用默认记录方式，不能是 DEFAULT
     * @param resolverLookup 审计目标解析器和 payload 工厂查找入口
     * @param clock 审计发生时间使用的时钟
     */
    public AuditCallInterceptor(
            @NotNull List<? extends @NotNull AuditRecorder> recorders,
            @NotNull Audit.Mode defaultMode,
            @NotNull AuditResolverLookup resolverLookup,
            @NotNull SPICallValueResolver expressionResolver,
            @NotNull Clock clock) {
        this.defaultMode = Objects.requireNonNull(defaultMode);
        if (defaultMode == Audit.Mode.DEFAULT) {
            throw new IllegalArgumentException("应用审计配置不能使用 DEFAULT");
        }
        Map<Audit.Mode, AuditRecorder> installed = new EnumMap<>(Audit.Mode.class);
        for (AuditRecorder recorder : Objects.requireNonNull(recorders)) {
            Audit.Mode mode = Objects.requireNonNull(recorder.mode());
            if (mode == Audit.Mode.DEFAULT || installed.putIfAbsent(mode, recorder) != null) {
                throw new IllegalArgumentException("审计记录方式无效或重复: " + mode);
            }
        }
        if (!installed.containsKey(defaultMode)) {
            throw new IllegalStateException("没有安装应用默认审计记录方式: " + defaultMode);
        }
        this.recorders = Map.copyOf(installed);
        this.resolverLookup = Objects.requireNonNull(resolverLookup, "审计解析器目录不能为空");
        this.expressionResolver = Objects.requireNonNull(expressionResolver, "注解表达式解析器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public boolean supports(@NotNull CallExecution execution) {
        return execution.declaration(Audit.class).isPresent();
    }

    @Override
    public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Audit audit = checked.declaration(Audit.class).orElseThrow();
        Audit.Mode mode = checked.declarations(Audit.class)
                .stream()
                .map(Audit::mode)
                .filter(value -> value != Audit.Mode.DEFAULT)
                .findFirst()
                .orElse(defaultMode);
        AuditRecorder recorder = recorders.get(mode);
        if (recorder == null) {
            throw new IllegalStateException("没有安装方法声明的审计记录方式: " + mode);
        }

        // 在业务执行前确认方式可用；完成后只读取已经确定的身份和实际结果。
        CallOutcome outcome = next.proceed();
        checked.recordOutcome(outcome);
        if ((outcome.status() == CallOutcome.Status.DENIED || outcome.status() == CallOutcome.Status.FAILED)
                && (checked.call().subject().isEmpty() || checked.call().principal().isEmpty())) {
            return outcome;
        }
        recorder.record(auditRecord(audit, checked, auditResult(outcome)));
        LOG.trace(
                "调用审计已完成记录或可靠交接 | callId={} mode={} outcome={}",
                checked.call().callId(),
                mode,
                outcome.status());
        return outcome;
    }

    /** 在异步业务完成后建立并记录最终审计结果。 */
    @Override
    public @NotNull CompletionStage<CallOutcome> aroundAsync(
            @NotNull CallExecution execution,
            @NotNull CallLifecycle.AsyncNext next) {
        CallExecution checked = Objects.requireNonNull(execution, "方法执行不能为空");
        Audit audit = checked.declaration(Audit.class).orElseThrow();
        Audit.Mode mode = checked.declarations(Audit.class)
                .stream()
                .map(Audit::mode)
                .filter(value -> value != Audit.Mode.DEFAULT)
                .findFirst()
                .orElse(defaultMode);
        AuditRecorder recorder = recorders.get(mode);
        if (recorder == null) {
            throw new IllegalStateException("没有安装方法声明的审计记录方式: " + mode);
        }
        return next.proceedAsync().thenApply(outcome -> {
            checked.recordOutcome(outcome);
            if ((outcome.status() == CallOutcome.Status.DENIED || outcome.status() == CallOutcome.Status.FAILED)
                    && (checked.call().subject().isEmpty() || checked.call().principal().isEmpty())) {
                return outcome;
            }
            try {
                recorder.record(auditRecord(audit, checked, auditResult(outcome)));
                LOG.trace(
                        "异步调用审计已完成记录或可靠交接 | callId={} mode={} outcome={}",
                        checked.call().callId(),
                        mode,
                        outcome.status());
                return outcome;
            } catch (RuntimeException failure) {
                return outcome.withCompletionFailure(failure);
            }
        });
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
        AuditPayload payload;
        if (!audit.payload().isBlank()) {
            if (audit.payloadFactory() != NoAuditPayloadFactory.class) {
                throw new IllegalStateException("Audit payload 与 payloadFactory 不能同时声明");
            }
            Object value = expressionResolver.resolve(audit.payload(), execution);
            if (value != null && !(value instanceof AuditPayload)) {
                throw new IllegalStateException("Audit payload 表达式必须返回 AuditPayload 或空值");
            }
            payload = (AuditPayload) value;
        } else {
            payload = audit.payloadFactory() == NoAuditPayloadFactory.class
                    ? null
                    : resolverLookup.payloadFactory(audit.payloadFactory()).create(invocation);
        }
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
                execution.call().input(HttpCall.KEY).orElse(null),
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
    private @Nullable Resource resolveTarget(Audit audit, AuditInvocation invocation) {
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
        boolean expression = !audit.target().isBlank();
        if ((resolver != null ? 1 : 0) + (markedTarget != null ? 1 : 0) + (expression ? 1 : 0) > 1) {
            throw new IllegalStateException("Audit 不能同时使用 target 表达式、targetResolver 和 @AuditTarget 参数");
        }
        if (expression) {
            Object value = expressionResolver.resolve(audit.target(), invocation.execution());
            return value == null ? null : ResourceReference.fromValue(value, audit.resourceType());
        }
        if (resolver != null) {
            Resource value = resolver.resolve(invocation);
            return value == null ? null : ResourceReference.fromValue(value, audit.resourceType());
        }
        if (markedTarget == null) {
            return null;
        }
        return ResourceReference.fromValue(
                invocation.arguments()[markedIndex],
                markedTarget.resourceType(),
                markedTarget.idPrefix());
    }

}
