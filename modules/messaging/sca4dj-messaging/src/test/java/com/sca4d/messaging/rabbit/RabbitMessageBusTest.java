package com.sca4d.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.messaging.outbox.MessageOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;

/** 验证允许丢失的 RabbitMQ 发布只在事务成功提交后尝试一次。 */
class RabbitMessageBusTest {
    static {
        AppName.publish("sca4d-test");
    }

    private static final TreeCode CODE = TreeCode.of("TEST:MESSAGE");

    private final IdGenerator ids = mock(IdGenerator.class);

    private final RabbitMessageEnvelopeCodec codec = mock(RabbitMessageEnvelopeCodec.class);

    private final RabbitPublisher publisher = mock(RabbitPublisher.class);

    private final Message message = mock(Message.class);

    private RabbitMessageBus bus;

    @BeforeEach
    void setUp() {
        when(ids.nextId()).thenReturn(Id.of(42L));
        when(message.code()).thenReturn(CODE);
        when(codec.encode(any(), eq(message), any(), eq(new Name("SCA4D_TEST")))).thenReturn(new byte[] {1, 2, 3});
        bus = new RabbitMessageBus(
                ids,
                codec,
                publisher,
                mock(MessageOutboxRepository.class),
                Clock.systemUTC());
    }

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
    void requiresTransactionForDeferredDispatch() {
        assertThatThrownBy(() -> bus.dispatch(message, DispatchPolicy.afterCommit()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void attemptsAfterCommitAndContainsAllowedLoss() {
        beginTransaction();
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher)
                .send(any(), eq(CODE), any());

        DispatchReceipt receipt = bus.dispatch(message, DispatchPolicy.afterCommit());
        assertThat(receipt.status()).isEqualTo(DispatchReceipt.Status.AFTER_COMMIT_REGISTERED);
        verify(publisher, never()).send(any(), any(), any());

        assertThatCode(
                () -> TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
        verify(publisher).send(Id.of(42L), CODE, new byte[] {1, 2, 3});
    }

    @Test
    void doesNotPublishAfterRollback() {
        beginTransaction();
        bus.dispatch(message, DispatchPolicy.afterCommit());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(publisher, never()).send(any(), any(), any());
    }

    /** 建立带同步的真实事务标记。 */
    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
