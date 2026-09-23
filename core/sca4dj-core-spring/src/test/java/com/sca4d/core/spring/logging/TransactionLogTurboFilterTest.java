package com.sca4d.core.spring.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sca4d.core.api.logging.LogMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 验证提交后 INFO 只在事务提交后按原 logger 和 MDC 输出。 */
class TransactionLogTurboFilterTest {

    private LoggerContext context;

    private Logger logger;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        context.setMDCAdapter(MDC.getMDCAdapter());
        context.start();
        TransactionLogTurboFilter filter = new TransactionLogTurboFilter();
        filter.setContext(context);
        filter.start();
        context.addTurboFilter(filter);
        logger = context.getLogger("com.sca4d.test.TransactionLog");
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
        events = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        events.setContext(context);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(
                            synchronization -> synchronization
                                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        MDC.clear();
        context.stop();
    }

    @Test
    void writesMarkedInfoOnceAfterCommitWithRegistrationContext() {
        beginTransaction();
        MDC.put("callId", "call-42");
        logger.atInfo().addMarker(LogMark.AFTER_COMMIT).log("事务已提交 | targetId={}", 42L);
        MDC.put("callId", "changed");

        assertThat(events.list).isEmpty();
        commit();
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("事务已提交 | targetId=42");
            assertThat(event.getLoggerName()).isEqualTo(logger.getName());
            assertThat(event.getMDCPropertyMap()).containsEntry("callId", "call-42");
            assertThat(event.getMarkerList()).isNullOrEmpty();
        });
        assertThat(MDC.get("callId")).isEqualTo("changed");
    }

    @Test
    void leavesOrdinaryInfoUnchangedAndDropsMarkedInfoAfterRollback() {
        logger.info("普通日志 | targetId={}", 41L);
        beginTransaction();
        logger.atInfo().addMarker(LogMark.AFTER_COMMIT).log("提交日志 | targetId={}", 42L);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("普通日志 | targetId=41");
    }

    @Test
    void rejectsMissingTransactionAndUnsupportedMessages() {
        assertThatThrownBy(() -> logger.atInfo().addMarker(LogMark.AFTER_COMMIT).log("无事务"))
                .isInstanceOf(IllegalStateException.class);
        beginTransaction();

        assertThatThrownBy(() -> logger.atDebug().addMarker(LogMark.AFTER_COMMIT).log("错误级别"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> logger.atInfo()
                        .addMarker(LogMark.AFTER_COMMIT)
                        .setCause(new IllegalStateException("failure"))
                        .log("错误异常"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> logger.atInfo()
                        .addMarker(LogMark.AFTER_COMMIT)
                        .addKeyValue("id", 42L)
                        .log("额外字段"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> logger.atInfo()
                        .addMarker(LogMark.AFTER_COMMIT)
                        .log("可变参数 | value={}", new StringBuilder("value")))
                .isInstanceOf(IllegalArgumentException.class);

        Marker composite = MarkerFactory.getDetachedMarker("COMPOSITE");
        composite.add(LogMark.AFTER_COMMIT);
        assertThatThrownBy(() -> logger.info(composite, "组合 Marker"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void ignoresLevelProbeAndRegistersCompleteFluentEventOnce() {
        beginTransaction();
        assertThat(logger.isInfoEnabled(LogMark.AFTER_COMMIT)).isTrue();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();

        logger.atInfo().addMarker(LogMark.AFTER_COMMIT).log("最终结果 | targetId={}", 42L);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        commit();
        assertThat(events.list).hasSize(1);
    }

    /** 建立带同步的真实事务标记。 */
    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    /** 模拟 Spring 提交动作和事务完成回调。 */
    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }
}
