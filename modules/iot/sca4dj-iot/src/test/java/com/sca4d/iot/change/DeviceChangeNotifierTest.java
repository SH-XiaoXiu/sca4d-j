package com.sca4d.iot.change;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.change.DeviceChangePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

/** 验证 Device 通知在数据库提交后发布，不在事务中的变更立即发布。 */
class DeviceChangeNotifierTest {

    private final DeviceChangePublisher publisher = mock(DeviceChangePublisher.class);

    private final DeviceChangeNotifier notifier = new DeviceChangeNotifier(List.of(publisher));

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesImmediatelyWithoutTransaction() {
        notifier.changed(42L, DeviceChangeArea.DEVICE, Instant.EPOCH);

        verify(publisher).publish(any());
    }

    @Test
    void publishesAfterCommitAndDiscardsAfterRollback() {
        beginTransaction();
        notifier.changed(42L, DeviceChangeArea.DEVICE, Instant.EPOCH);
        verify(publisher, never()).publish(any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(publisher).publish(any());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        beginTransaction();
        notifier.changed(43L, DeviceChangeArea.DEVICE, Instant.EPOCH);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(publisher).publish(any());
    }

    /** 建立带同步的真实事务标记。 */
    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
