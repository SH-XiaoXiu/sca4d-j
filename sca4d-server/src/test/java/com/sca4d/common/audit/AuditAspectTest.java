package com.sca4d.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.concurrent.SpelKeyEvaluator;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.config.properties.Sca4djAuditProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/** 验证审计切面对成功、失败、同步事务和异步记录的处理。 */
class AuditAspectTest {

    @Test
    void shouldRejectBlankAuditModuleBeforeBusinessExecution() throws Throwable {
        AuditAspect aspect = new AuditAspect(
                mock(AuditRecorder.class),
                mock(AuditSource.class),
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        InvalidAuditedTarget target = new InvalidAuditedTarget();
        Method method = InvalidAuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("@Audit module 和 action 必须是有效文本");
        verify(joinPoint, Mockito.never()).proceed();
    }

    @Test
    void shouldPreserveSuccessfulBusinessResultWhenAuditCannotBeWritten() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        Mockito.doThrow(new IllegalStateException("audit failed"))
                .when(recorder)
                .record(ArgumentMatchers.any());
        AuditSource source = mock(AuditSource.class);
        when(source.actor()).thenReturn(new AuditActor());
        when(source.request()).thenReturn(new AuditRequest());
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        AuditedTarget target = new AuditedTarget();
        Method method = AuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenReturn("done");

        assertThat(aspect.around(joinPoint)).isEqualTo("done");
        verify(recorder).record(ArgumentMatchers.any());
    }

    @Test
    void shouldPreserveCommittedBusinessResultWhenAfterCommitAuditFails() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        Mockito.doThrow(new IllegalStateException("audit failed"))
                .when(recorder)
                .record(ArgumentMatchers.any());
        AuditSource source = mock(AuditSource.class);
        when(source.actor()).thenReturn(new AuditActor());
        when(source.request()).thenReturn(new AuditRequest());
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        AuditedTarget target = new AuditedTarget();
        Method method = AuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenReturn("done");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThat(aspect.around(joinPoint)).isEqualTo("done");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
            assertThatCode(synchronization::afterCommit).doesNotThrowAnyException();
            verify(recorder).record(ArgumentMatchers.any());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldSubmitImmediatelyWhenOnlySynchronizationIsActive() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        List<Runnable> tasks = new ArrayList<>();
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                tasks::add,
                null,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(AuditedTarget.class, "execute", "done");
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(aspect.around(joinPoint)).isEqualTo("done");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            assertThat(tasks).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        tasks.getFirst().run();
        verify(recorder).record(ArgumentMatchers.any());
    }

    @Test
    void shouldPreserveBusinessFailureWhenFailureAuditCannotBeWritten() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        Mockito.doThrow(new IllegalStateException("audit failed"))
                .when(recorder)
                .record(ArgumentMatchers.any());
        AuditSource source = mock(AuditSource.class);
        when(source.actor()).thenReturn(new AuditActor());
        when(source.request()).thenReturn(new AuditRequest());
        SpelKeyEvaluator keyEvaluator = mock(SpelKeyEvaluator.class);
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                keyEvaluator,
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        AuditedTarget target = new AuditedTarget();
        Method method = AuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        IllegalArgumentException businessFailure = new IllegalArgumentException("business failed");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenThrow(businessFailure);

        Throwable thrown = catchThrowable(() -> aspect.around(joinPoint));

        assertThat(thrown).isSameAs(businessFailure);
        assertThat(thrown.getSuppressed()).isEmpty();
    }

    @Test
    void shouldNotPersistInternalExceptionMessage() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSource source = mock(AuditSource.class);
        when(source.actor()).thenReturn(new AuditActor());
        when(source.request()).thenReturn(new AuditRequest());
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        AuditedTarget target = new AuditedTarget();
        Method method = AuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("password=secret"));

        catchThrowable(() -> aspect.around(joinPoint));

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getError().getType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(recordCaptor.getValue().getError().getMessageKey()).isNull();
        assertThat(recordCaptor.getValue().getError().getMessageArgs()).isEmpty();
    }

    @Test
    void shouldPersistBusinessErrorMessageReference() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSource source = mock(AuditSource.class);
        when(source.actor()).thenReturn(new AuditActor());
        when(source.request()).thenReturn(new AuditRequest());
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(AuditedTarget.class, "execute", null);
        when(joinPoint.proceed()).thenThrow(new BizException(CommonErrorCode.BAD_REQUEST, "field"));

        catchThrowable(() -> aspect.around(joinPoint));

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getError().getMessageKey())
                .isEqualTo(CommonErrorCode.BAD_REQUEST.getMessageKey());
        assertThat(recordCaptor.getValue().getError().getMessageArgs()).containsExactly("field");
    }

    @Test
    void shouldReturnBeforeAsyncAuditTaskRuns() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSource source = auditSource();
        List<Runnable> tasks = new ArrayList<>();
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                tasks::add,
                null,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(AuditedTarget.class, "execute", "done");

        assertThat(aspect.around(joinPoint)).isEqualTo("done");

        verify(recorder, Mockito.never()).record(ArgumentMatchers.any());
        assertThat(tasks).hasSize(1);
        tasks.getFirst().run();
        verify(recorder).record(ArgumentMatchers.any());
    }

    @Test
    void shouldCommitBusinessAndAuditInSameSyncTransaction() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(status);
        Sca4djAuditProperties properties = new Sca4djAuditProperties();
        properties.setMode(AuditMode.SYNC);
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                properties,
                Runnable::run,
                transactionManager,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(AuditedTarget.class, "execute", "done");

        assertThat(aspect.around(joinPoint)).isEqualTo("done");

        InOrder inOrder = Mockito.inOrder(joinPoint, recorder, transactionManager);
        ArgumentCaptor<TransactionDefinition> definitionCaptor = ArgumentCaptor.forClass(TransactionDefinition.class);
        inOrder.verify(transactionManager).getTransaction(definitionCaptor.capture());
        inOrder.verify(joinPoint).proceed();
        inOrder.verify(recorder).record(ArgumentMatchers.any());
        inOrder.verify(transactionManager).commit(status);
        assertThat(definitionCaptor.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Test
    void shouldRollbackSyncBusinessWhenAuditWriteFails() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        Mockito.doThrow(new IllegalStateException("audit failed"))
                .when(recorder)
                .record(ArgumentMatchers.any());
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(status);
        Sca4djAuditProperties properties = new Sca4djAuditProperties();
        properties.setMode(AuditMode.SYNC);
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                properties,
                Runnable::run,
                transactionManager,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(AuditedTarget.class, "execute", "done");

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit failed");
        verify(transactionManager).rollback(status);
        verify(transactionManager, Mockito.never()).commit(status);
    }

    @Test
    void shouldRecordSyncBusinessFailureAsAsyncIndependentAudit() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus businessStatus = mock(TransactionStatus.class);
        TransactionStatus auditStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(businessStatus, auditStatus);
        Sca4djAuditProperties properties = new Sca4djAuditProperties();
        properties.setMode(AuditMode.SYNC);
        List<Runnable> tasks = new ArrayList<>();
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                properties,
                tasks::add,
                transactionManager,
                new SimpleMeterRegistry());
        IllegalArgumentException businessFailure = new IllegalArgumentException("business failed");
        AuditedTarget target = new AuditedTarget();
        Method method = AuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenThrow(businessFailure);

        Throwable thrown = catchThrowable(() -> aspect.around(joinPoint));

        assertThat(thrown).isSameAs(businessFailure);
        verify(transactionManager).rollback(businessStatus);
        verify(recorder, Mockito.never()).record(ArgumentMatchers.any());
        assertThat(tasks).hasSize(1);
        tasks.getFirst().run();
        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getResult()).isEqualTo(AuditResult.FAILED);
        verify(transactionManager).commit(auditStatus);
        ArgumentCaptor<TransactionDefinition> definitionCaptor = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, Mockito.times(2)).getTransaction(definitionCaptor.capture());
        assertThat(definitionCaptor.getAllValues())
                .extracting(TransactionDefinition::getPropagationBehavior)
                .containsExactly(
                        TransactionDefinition.PROPAGATION_REQUIRED,
                        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void annotationModeShouldOverrideGlobalAsyncMode() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(status);
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                task -> {
                    throw new AssertionError("SYNC 注解不应提交异步成功审计");
                },
                transactionManager,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(SyncAuditedTarget.class, "execute", "done");

        assertThat(aspect.around(joinPoint)).isEqualTo("done");
        verify(recorder).record(ArgumentMatchers.any());
        verify(transactionManager).commit(status);
    }

    @Test
    void shouldRejectAsyncTaskWithoutChangingBusinessResult() throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor rejectingExecutor = task -> {
            throw new RejectedExecutionException("full");
        };
        AuditAspect aspect = new AuditAspect(
                mock(AuditRecorder.class),
                auditSource(),
                mock(SpelKeyEvaluator.class),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                rejectingExecutor,
                null,
                meterRegistry);
        ProceedingJoinPoint joinPoint = joinPoint(AuditedTarget.class, "execute", "done");

        assertThat(aspect.around(joinPoint)).isEqualTo("done");
        assertThat(meterRegistry.counter("sca4dj.audit.async.rejected").count()).isEqualTo(1D);
    }

    @Test
    void shouldCaptureTargetAndMapPayloadFromRealBusinessResult() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        Map<String, Object> result = Map.of("id", 9L, "count", 3);
        ProceedingJoinPoint joinPoint = joinPoint(ResultAuditedTarget.class, "execute", result);

        assertThat(aspect.around(joinPoint)).isSameAs(result);

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        AuditRecord record = recordCaptor.getValue();
        assertThat(record.getTarget().getId()).isEqualTo("9");
        assertThat(record.getPayload()).isInstanceOf(RawAuditPayload.class);
        assertThat(((RawAuditPayload) record.getPayload()).getValues()).containsEntry("count", 3);
        assertThat(record.getPayloadType()).isEqualTo(AuditCodec.RAW_PAYLOAD_TYPE);
    }

    @Test
    void shouldCaptureCreatedTargetLabelAfterBusinessResultProvidesId() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSource source = auditSource();
        when(source.targetLabel("demo", "9")).thenReturn("新建资源");
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        Map<String, Object> result = Map.of("id", 9L);
        ProceedingJoinPoint joinPoint = joinPoint(ResultAuditedTarget.class, "execute", result);

        assertThat(aspect.around(joinPoint)).isSameAs(result);

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getTarget().getLabel()).isEqualTo("新建资源");
        verify(source).targetLabel("demo", "9");
    }

    @Test
    void shouldKeepTargetLabelCapturedBeforeExistingResourceChanges() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSource source = auditSource();
        when(source.targetLabel("demo", "9")).thenReturn("操作前名称").thenReturn(null);
        AuditAspect aspect = new AuditAspect(
                recorder,
                source,
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(ExistingTargetAuditedTarget.class, "execute", null);

        assertThat(aspect.around(joinPoint)).isNull();

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getTarget().getLabel()).isEqualTo("操作前名称");
        verify(source).targetLabel("demo", "9");
    }

    @Test
    void shouldRecordBusinessFailureWithNullSafeResultDependentExpressions() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        ResultAuditedTarget target = new ResultAuditedTarget();
        Method method = ResultAuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        IllegalStateException failure = new IllegalStateException("failed");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(failure);

        assertThat(catchThrowable(() -> aspect.around(joinPoint))).isSameAs(failure);

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(recordCaptor.getValue().getTarget().getId()).isNull();
        assertThat(recordCaptor.getValue().getPayload()).isInstanceOf(RawAuditPayload.class);
    }

    @Test
    void shouldEvaluateFailurePayloadThatUsesResultSafelyAndNeedsError() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        FailureAwareAuditedTarget target = new FailureAwareAuditedTarget();
        Method method = FailureAwareAuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        IllegalStateException failure = new IllegalStateException("failed");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(failure);

        assertThat(catchThrowable(() -> aspect.around(joinPoint))).isSameAs(failure);

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        RawAuditPayload payload = (RawAuditPayload) recordCaptor.getValue().getPayload();
        assertThat(payload.getValues())
                .containsEntry("error", "failed")
                .containsEntry("resultMissing", true);
    }

    @Test
    void shouldReportMisconfiguredFailureExpressionInsteadOfSilentlyDroppingIt() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                meterRegistry);
        InvalidFailureAuditedTarget target = new InvalidFailureAuditedTarget();
        Method method = InvalidFailureAuditedTarget.class.getDeclaredMethod("execute");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        IllegalStateException failure = new IllegalStateException("failed");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(failure);

        assertThat(catchThrowable(() -> aspect.around(joinPoint))).isSameAs(failure);

        verifyNoInteractions(recorder);
        assertThat(meterRegistry.counter("sca4dj.audit.write.failures").count()).isEqualTo(1D);
    }

    @Test
    void shouldCaptureTypedPayloadOnlyThroughExactCodec() throws Throwable {
        AuditRecorder recorder = mock(AuditRecorder.class);
        TestAuditPayloadCodec codec = new TestAuditPayloadCodec();
        AuditAspect aspect = new AuditAspect(
                recorder,
                auditSource(),
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of(codec)),
                new Sca4djAuditProperties(),
                Runnable::run,
                null,
                new SimpleMeterRegistry());
        TestAuditPayload payload = new TestAuditPayload("created");
        ProceedingJoinPoint joinPoint = joinPoint(TypedAuditedTarget.class, "execute", payload);

        assertThat(aspect.around(joinPoint)).isSameAs(payload);

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(recorder).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getPayload()).isSameAs(payload);
        assertThat(recordCaptor.getValue().getPayloadType()).isEqualTo("test.payload");
    }

    @Test
    void shouldRejectPojoPayloadAndRollbackSyncBusiness() throws Throwable {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(status);
        AuditAspect aspect = new AuditAspect(
                mock(AuditRecorder.class),
                auditSource(),
                new SpelKeyEvaluator(),
                new AuditPayloadRegistry(List.of()),
                new Sca4djAuditProperties(),
                Runnable::run,
                transactionManager,
                new SimpleMeterRegistry());
        ProceedingJoinPoint joinPoint = joinPoint(PojoAuditedTarget.class, "execute", new Object());

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注册 POJO");
        verify(transactionManager).rollback(status);
    }

    /**
     * 创建提供空操作者和请求数据的审计来源。
     *
     * @return 固定审计来源
     */
    private static @NotNull AuditSource auditSource() {
        AuditSource source = mock(AuditSource.class);
        when(source.actor()).thenReturn(new AuditActor());
        when(source.request()).thenReturn(new AuditRequest());
        return source;
    }

    /**
     * 创建指向指定测试方法的 AOP 调用点。
     *
     * @param targetType 审计目标类型
     * @param methodName 审计目标方法名
     * @param result 业务返回值
     * @return 已绑定目标、签名和返回值的调用点
     * @throws Throwable 无法创建调用点或绑定返回值时抛出
     */
    private static @NotNull ProceedingJoinPoint joinPoint(
            @NotNull Class<?> targetType, @NotNull String methodName, @Nullable Object result)
            throws Throwable {
        Object target;
        try {
            target = targetType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法创建审计测试目标", exception);
        }
        Method method = targetType.getDeclaredMethod(methodName);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    /** 记录成功方法调用及参数的异步审计目标。 */
    static class AuditedTarget {

        @Audit(module = "test", action = "execute")
        void execute() {
        }
    }

    /** 要求在当前事务中同步记录的审计目标。 */
    static class SyncAuditedTarget {

        @Audit(module = "test", action = "execute", mode = AuditMode.SYNC)
        void execute() {
        }
    }

    /** 声明无效同步事务配置以验证启动检查的审计目标。 */
    static class InvalidAuditedTarget {

        @Audit(module = " ", action = "execute")
        void execute() {
        }
    }

    /** 从方法返回值生成审计内容的目标。 */
    static class ResultAuditedTarget {

        @Audit(module = "test", action = "result", targetType = "demo", targetId = "#result == null ? null : #result['id']", payload = "#result")
        Object execute() {
            return null;
        }
    }

    /** 使用调用前已查询资源名称生成审计目标快照的目标。 */
    static class ExistingTargetAuditedTarget {

        @Audit(module = "test", action = "existing", targetType = "demo", targetId = "'9'")
        Object execute() {
            return null;
        }
    }

    /** 方法失败后仍从参数生成审计内容的目标。 */
    static class FailureAwareAuditedTarget {

        @Audit(module = "test", action = "failure", payload = "T(java.util.Map).of('error', #error.message, 'resultMissing', #result == null)")
        Object execute() {
            return null;
        }
    }

    /** 失败后无法生成合法审计内容时用于验证降级处理的目标。 */
    static class InvalidFailureAuditedTarget {

        @Audit(module = "test", action = "invalid-failure", payload = "#error.missingProperty")
        Object execute() {
            return null;
        }
    }

    /** 生成已注册类型化审计内容的目标。 */
    static class TypedAuditedTarget {

        @Audit(module = "test", action = "typed", payloadType = "test.payload", payload = "#result")
        Object execute() {
            return null;
        }
    }

    /** 返回未实现 AuditPayload 的普通对象以验证拒绝结果的目标。 */
    static class PojoAuditedTarget {

        @Audit(module = "test", action = "pojo", mode = AuditMode.SYNC, payload = "#result")
        Object execute() {
            return null;
        }
    }

    /** 审计切面测试中保存单个 name 字段的类型化内容。 */
    static class TestAuditPayload implements AuditPayload {
        private final String action;

        TestAuditPayload(String action) {
            this.action = action;
        }

        String getAction() {
            return action;
        }
    }

    /** 为 TestAuditPayload 提供固定类型名和 Java 类型的 Codec。 */
    static class TestAuditPayloadCodec implements AuditPayloadCodec<TestAuditPayload> {

        @Override
        public @NotNull String payloadType() {
            return "test.payload";
        }

        @Override
        public @NotNull Class<TestAuditPayload> payloadClass() {
            return TestAuditPayload.class;
        }
    }
}
