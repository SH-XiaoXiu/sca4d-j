package com.sca4d.core.spring.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/** 验证提交后动作的事务边界、顺序、嵌套事务隔离和失败结果。 */
class TransactionCallbacksTest {

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(
                            synchronization -> synchronization
                                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void requiresTransactionForStrictCallbackAndRunsFlexibleCallbackOutsideTransaction() {
        List<String> events = new ArrayList<>();
        assertThatThrownBy(() -> TransactionCallbacks.afterCommit(() -> events.add("strict")))
                .isInstanceOf(IllegalStateException.class);

        TransactionCallbacks.afterCommitOrNow(() -> events.add("immediate"));
        assertThat(events).containsExactly("immediate");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> TransactionCallbacks.afterCommitOrNow(() -> events.add("early")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(events).containsExactly("immediate");
    }

    @Test
    void runsCommittedCallbacksOnceInRegistrationOrderIncludingNestedRegistration() {
        beginTransaction();
        List<String> events = new ArrayList<>();
        TransactionCallbacks.afterCommit(() -> {
            events.add("first");
            TransactionCallbacks.afterCommit(() -> events.add("nested"));
        });
        TransactionCallbacks.afterCommitOrNow(() -> events.add("second"));

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        assertThat(events).isEmpty();
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(events).containsExactly("first", "second", "nested");
    }

    @Test
    void discardsCallbacksAfterRollback() {
        beginTransaction();
        List<String> events = new ArrayList<>();
        TransactionCallbacks.afterCommit(() -> events.add("unexpected"));

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(events).isEmpty();
    }

    @Test
    void isolatesCallbacksWhenOuterTransactionIsSuspended() {
        beginTransaction();
        List<String> events = new ArrayList<>();
        TransactionCallbacks.afterCommit(() -> events.add("outer"));
        TransactionSynchronization outer = TransactionSynchronizationManager.getSynchronizations().getFirst();

        outer.suspend();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        beginTransaction();
        TransactionCallbacks.afterCommit(() -> events.add("inner"));
        complete(TransactionSynchronization.STATUS_COMMITTED);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        outer.resume();
        TransactionSynchronizationManager.registerSynchronization(outer);
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(events).containsExactly("inner", "outer");
    }

    @Test
    void continuesAfterRuntimeFailureAndAggregatesExceptions() {
        beginTransaction();
        List<String> events = new ArrayList<>();
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");
        TransactionCallbacks.afterCommit(() -> {
            events.add("one");
            throw first;
        });
        TransactionCallbacks.afterCommit(() -> events.add("two"));
        TransactionCallbacks.afterCommit(() -> {
            events.add("three");
            throw second;
        });

        assertThatThrownBy(
                () -> TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit))
                .isSameAs(first)
                .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(second));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(
                        synchronization -> synchronization
                                .afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(events).containsExactly("one", "two", "three");
    }

    /** 建立带同步的真实事务标记。 */
    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    /** 模拟 Spring 成功提交或回滚后的同步回调。 */
    private static void complete(int status) {
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            callbacks.forEach(TransactionSynchronization::afterCommit);
        }
        callbacks.forEach(callback -> callback.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
