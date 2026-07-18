package com.sca4d.common.audit;

import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.MessageReference;
import com.sca4d.config.properties.Sca4djAuditProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 审计切面。 */
@Aspect
@Order(300)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditRecorder recorder;
    private final AuditSource source;
    private final SpelKeyEvaluator keyEvaluator;
    private final AuditPayloadRegistry payloadRegistry;
    private final Sca4djAuditProperties properties;
    private final TaskExecutor auditExecutor;
    private final PlatformTransactionManager transactionManager;
    private final Counter rejectedCounter;
    private final Counter writeFailureCounter;

    /**
     * 创建支持异步独立记录和同步事务记录的审计切面。
     *
     * @param recorder 保存审计记录的实现
     * @param source 当前请求的操作者和请求数据来源
     * @param keyEvaluator 审计注解 SpEL 表达式解析器
     * @param payloadRegistry 审计载荷类型注册表
     * @param properties 审计开关、成功失败记录和执行模式配置
     * @param auditExecutor 审计专用异步执行器
     * @param transactionManager 当前应用唯一事务管理器；无事务记录器场景允许缺失
     * @param meterRegistry 审计拒绝和写入失败指标注册表
     */
    public AuditAspect(
            @NotNull AuditRecorder recorder,
            @NotNull AuditSource source,
            @NotNull SpelKeyEvaluator keyEvaluator,
            @NotNull AuditPayloadRegistry payloadRegistry,
            @NotNull Sca4djAuditProperties properties,
            @NotNull TaskExecutor auditExecutor,
            @Nullable PlatformTransactionManager transactionManager,
            @NotNull MeterRegistry meterRegistry) {
        this.recorder = recorder;
        this.source = source;
        this.keyEvaluator = keyEvaluator;
        this.payloadRegistry = payloadRegistry;
        this.properties = properties;
        this.auditExecutor = auditExecutor;
        this.transactionManager = transactionManager;
        this.rejectedCounter = meterRegistry.counter("sca4dj.audit.async.rejected");
        this.writeFailureCounter = meterRegistry.counter("sca4dj.audit.write.failures");
    }

    /**
     * 在声明了审计的类或方法外包裹审计逻辑。
     *
     * @param joinPoint 切点
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(com.sca4d.common.audit.Audit) || @within(com.sca4d.common.audit.Audit)")
    public Object around(@NotNull ProceedingJoinPoint joinPoint) throws Throwable {
        Audit audit = findAudit(joinPoint);
        if (audit == null || !properties.isEnabled()) {
            return joinPoint.proceed();
        }
        if (!StringUtils.hasText(audit.module()) || !StringUtils.hasText(audit.action())) {
            throw new IllegalStateException("@Audit module 和 action 必须是有效文本");
        }
        AuditMode mode = audit.mode() == AuditMode.DEFAULT ? properties.getMode() : audit.mode();
        if (mode == AuditMode.DEFAULT) {
            throw new IllegalStateException("全局审计模式不能配置为 DEFAULT");
        }
        return mode == AuditMode.SYNC ? aroundSync(joinPoint, audit) : aroundAsync(joinPoint, audit);
    }

    /**
     * 执行不影响业务结果的异步审计。
     *
     * @param joinPoint 当前 AOP 调用点
     * @param audit 当前审计声明
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    private Object aroundAsync(@NotNull ProceedingJoinPoint joinPoint, @NotNull Audit audit) throws Throwable {
        AuditTarget targetBeforeExecution = target(joinPoint, audit, null, null);
        captureTargetLabel(targetBeforeExecution);
        try {
            Object result = joinPoint.proceed();
            if (properties.isRecordSuccess()) {
                try {
                    submitAfterCommit(createRecord(joinPoint, audit, AuditResult.SUCCESS, result, null, targetBeforeExecution), audit);
                } catch (RuntimeException exception) {
                    writeFailureCounter.increment();
                    log.error("成功审计采集异常，保留原业务结果 | auditModule={} auditAction={}",
                            audit.module(), audit.action(), exception);
                }
            }
            return result;
        } catch (Throwable throwable) {
            if (properties.isRecordFailure()) {
                try {
                    submitAsync(createRecord(joinPoint, audit, AuditResult.FAILED, null, throwable, targetBeforeExecution), audit);
                } catch (RuntimeException exception) {
                    writeFailureCounter.increment();
                    log.error("失败审计采集异常，保留原业务异常 | auditModule={} auditAction={}",
                            audit.module(), audit.action(), exception);
                }
            }
            throw throwable;
        }
    }

    /**
     * 执行业务和成功审计共用同一事务的同步审计。
     *
     * @param joinPoint 当前 AOP 调用点
     * @param audit 当前审计声明
     * @return 原方法返回值
     * @throws Throwable 业务、审计或事务异常
     */
    private Object aroundSync(@NotNull ProceedingJoinPoint joinPoint, @NotNull Audit audit) throws Throwable {
        if (transactionManager == null) {
            throw new IllegalStateException("同步审计模式需要唯一的事务管理器");
        }
        AuditTarget targetBeforeExecution = target(joinPoint, audit, null, null);
        captureTargetLabel(targetBeforeExecution);
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED);
        definition.setName("audit:" + audit.module().trim() + ":" + audit.action().trim());
        TransactionStatus status = transactionManager.getTransaction(definition);
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            rollback(status, throwable);
            if (properties.isRecordFailure()) {
                try {
                    submitAsync(createRecord(joinPoint, audit, AuditResult.FAILED, null, throwable, targetBeforeExecution), audit);
                } catch (RuntimeException exception) {
                    writeFailureCounter.increment();
                    log.error("失败审计采集异常，保留原业务异常 | auditModule={} auditAction={}",
                            audit.module(), audit.action(), exception);
                }
            }
            throw throwable;
        }
        try {
            if (properties.isRecordSuccess()) {
                recorder.record(createRecord(joinPoint, audit, AuditResult.SUCCESS, result, null, targetBeforeExecution));
            }
            transactionManager.commit(status);
            return result;
        } catch (RuntimeException exception) {
            rollback(status, exception);
            throw exception;
        }
    }

    /**
     * 采集一次方法执行产生的操作者、请求、目标、结果和异常数据。
     *
     * @param joinPoint 提供方法参数和目标对象的 AOP 调用点
     * @param audit 当前方法或类型的合并审计声明
     * @param result 业务执行结果
     * @param businessResult 成功业务方法的真实返回值，失败时为 null
     * @param throwable 失败结果对应的原始业务异常，成功时为 null
     * @return 已脱离请求线程上下文读取的完整审计记录
     */
    private @NotNull AuditRecord createRecord(
            @NotNull ProceedingJoinPoint joinPoint,
            @NotNull Audit audit,
            @NotNull AuditResult result,
            @Nullable Object businessResult,
            @Nullable Throwable throwable,
            @Nullable AuditTarget targetBeforeExecution) {
        AuditRecord record = new AuditRecord();
        record.setModuleCode(audit.module().trim());
        record.setActionCode(audit.action().trim());
        record.setResult(result);
        record.setActor(source.actor());
        record.setRequest(source.request());
        AuditTarget resolvedTarget = target(joinPoint, audit, businessResult, throwable);
        if (resolvedTarget != null && targetBeforeExecution != null
                && Objects.equals(resolvedTarget.getType(), targetBeforeExecution.getType())
                && Objects.equals(resolvedTarget.getId(), targetBeforeExecution.getId())) {
            resolvedTarget.setLabel(targetBeforeExecution.getLabel());
        }
        captureTargetLabel(resolvedTarget);
        record.setTarget(resolvedTarget);

        Object summaryValue = keyEvaluator.evaluateObject(audit.summary(), joinPoint, businessResult, throwable);
        if (summaryValue == null) {
            record.setSummary(null);
        } else if (summaryValue instanceof MessageReference summary) {
            record.setSummary(summary);
        } else if (summaryValue instanceof String summary && StringUtils.hasText(summary)) {
            record.setSummary(MessageReference.of(summary.trim()));
        } else {
            throw new IllegalStateException("@Audit summary 必须返回 MessageReference、有效文本或 null");
        }

        Object payloadValue = keyEvaluator.evaluateObject(audit.payload(), joinPoint, businessResult, throwable);
        AuditPayload payload;
        if (payloadValue == null) {
            payload = RawAuditPayload.empty();
        } else if (payloadValue instanceof AuditPayload auditPayload) {
            payload = auditPayload;
        } else if (payloadValue instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalStateException("@Audit payload Map 的键必须为 String");
                }
                values.put(key, entry.getValue());
            }
            payload = new RawAuditPayload(values);
        } else {
            throw new IllegalStateException("@Audit payload 禁止返回 Entity、View 或任意未注册 POJO");
        }
        record.setPayload(payload);
        AuditPayloadCodec<?> codec = payloadRegistry.find(payload);
        String inferredPayloadType;
        if (payload instanceof RawAuditPayload) {
            inferredPayloadType = AuditCodec.RAW_PAYLOAD_TYPE;
        } else if (codec != null) {
            inferredPayloadType = codec.payloadType();
        } else {
            throw new IllegalStateException("@Audit typed Payload 必须具有精确注册的 AuditPayloadCodec");
        }
        if (StringUtils.hasText(audit.payloadType())
                && !audit.payloadType().trim().equals(inferredPayloadType)) {
            throw new IllegalStateException("@Audit payloadType 与精确 Payload Codec 不一致");
        }
        record.setPayloadType(StringUtils.hasText(audit.payloadType())
                ? audit.payloadType().trim()
                : inferredPayloadType);
        record.setPayloadVersion(codec == null ? 1 : codec.payloadVersion());
        record.setCreatedAt(TimeSupport.now());
        if (throwable != null) {
            // 业务异常只保存固定消息引用；内部异常仅保存类型，避免把 SQL、凭据或第三方响应写入可查询审计详情。
            if (throwable instanceof BizException bizException) {
                record.setError(new AuditError(
                        throwable.getClass().getName(),
                        bizException.getMessageKey(),
                        bizException.getMessageArgs()));
            } else {
                record.setError(new AuditError(throwable.getClass().getName(), null, null));
            }
        }
        return record;
    }

    /**
     * 在当前业务事务成功提交后投递成功审计；没有活动事务时立即投递。
     *
     * @param record 已完成请求数据采集的审计记录
     * @param audit 当前审计声明
     */
    private void submitAfterCommit(@NotNull AuditRecord record, @NotNull Audit audit) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitAsync(record, audit);
                }
            });
            return;
        }
        submitAsync(record, audit);
    }

    /**
     * 将完整审计记录提交给有界执行器。
     *
     * @param record 已完成请求数据采集的审计记录
     * @param audit 当前审计声明
     */
    private void submitAsync(@NotNull AuditRecord record, @NotNull Audit audit) {
        try {
            auditExecutor.execute(() -> {
                try {
                    recordInNewTransaction(record);
                } catch (RuntimeException exception) {
                    writeFailureCounter.increment();
                    log.error("异步审计写入异常 | auditModule={} auditAction={} auditResult={}",
                            audit.module(), audit.action(), record.getResult(), exception);
                }
            });
        } catch (RuntimeException exception) {
            rejectedCounter.increment();
            log.error("异步审计任务被拒绝 | auditModule={} auditAction={} auditResult={}",
                    audit.module(), audit.action(), record.getResult(), exception);
        }
    }

    /**
     * 在独立事务中写入一条异步审计记录。
     *
     * @param record 已完成请求数据采集的审计记录
     */
    private void recordInNewTransaction(@NotNull AuditRecord record) {
        if (transactionManager == null) {
            recorder.record(record);
            return;
        }
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setName("audit:async");
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            recorder.record(record);
            transactionManager.commit(status);
        } catch (RuntimeException exception) {
            rollback(status, exception);
            throw exception;
        }
    }

    /**
     * 回滚尚未完成的事务，并将回滚异常附加到原始异常。
     *
     * @param status 当前事务状态
     * @param throwable 触发回滚的原始异常
     */
    private void rollback(@NotNull TransactionStatus status, @NotNull Throwable throwable) {
        if (status.isCompleted()) {
            return;
        }
        try {
            transactionManager.rollback(status);
        } catch (RuntimeException rollbackException) {
            throwable.addSuppressed(rollbackException);
        }
    }

    /**
     * 在业务方法完成后解析注解声明的审计目标。
     *
     * @param joinPoint 提供审计表达式参数的 AOP 调用点
     * @param audit 当前合并审计声明
     * @param result 成功业务返回值；失败时为 null
     * @param error 原始业务异常；成功时为 null
     * @return 未声明目标类型时返回 null，否则返回允许缺失目标标识的审计目标
     */
    private @Nullable AuditTarget target(
            @NotNull ProceedingJoinPoint joinPoint,
            @NotNull Audit audit,
            @Nullable Object result,
            @Nullable Throwable error) {
        if (!StringUtils.hasText(audit.targetType())) {
            return null;
        }
        Object value = keyEvaluator.evaluateObject(audit.targetId(), joinPoint, result, error);
        String targetId = value == null ? null : String.valueOf(value);
        return new AuditTarget(audit.targetType().trim(), StringUtils.hasText(targetId) ? targetId.trim() : null);
    }

    /** 在请求线程内为具有明确主键的目标补充删除后仍可读取的业务名称快照。 */
    private void captureTargetLabel(@Nullable AuditTarget target) {
        if (target == null || target.getLabel() != null || !StringUtils.hasText(target.getId())) {
            return;
        }
        target.setLabel(source.targetLabel(target.getType(), target.getId()));
    }

    /**
     * 按最具体实现方法优先、目标类型其次的顺序读取合并审计声明。
     *
     * @param joinPoint 当前 AOP 调用点
     * @return 方法或类型声明的审计配置，没有声明时返回 null
     */
    private @Nullable Audit findAudit(@NotNull ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (target != null) {
            method = AopUtils.getMostSpecificMethod(method, target.getClass());
        }
        Audit methodAudit = AnnotatedElementUtils.findMergedAnnotation(method, Audit.class);
        if (methodAudit != null) {
            return methodAudit;
        }
        Class<?> targetClass = target == null ? method.getDeclaringClass() : target.getClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, Audit.class);
    }
}
