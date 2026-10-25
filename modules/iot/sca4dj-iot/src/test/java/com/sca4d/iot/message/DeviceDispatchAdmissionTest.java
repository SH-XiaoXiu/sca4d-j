package com.sca4d.iot.message;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.name.Name;
import com.sca4d.iot.api.message.DeviceDispatchDecision;
import com.sca4d.iot.api.message.DeviceDispatchMessage;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.change.DeviceChangeNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

/** 验证资源等待不会进入 SENDING、消耗尝试或发布到网络。 */
class DeviceDispatchAdmissionTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

    private final DownlinkRepository repository = mock(DownlinkRepository.class);

    private final DeviceTransportRouter router = mock(DeviceTransportRouter.class);

    private final DeviceDeliveryScheduler scheduler = mock(DeviceDeliveryScheduler.class);

    private final DeviceChangeNotifier changes = mock(DeviceChangeNotifier.class);

    @Test
    void deferredAdmissionPreservesTheAttemptAndDoesNotStartAcknowledgementWaiting() {
        var handler = handler(DeviceDispatchDecision.deferUntil(NOW.plusSeconds(5)));
        handler.handle(delivery());
        verify(scheduler).publishDispatch(10, 1, NOW.plusSeconds(5));
        verify(repository, never()).beginDispatch(anyLong(), anyInt());
        verifyNoInteractions(router);
        verifyNoInteractions(changes);
        verifyNoMoreInteractions(scheduler);
    }

    @Test
    void rejectionTerminatesWithoutConsumingASendAttempt() {
        var handler = handler(DeviceDispatchDecision.reject(IotFailureCode.RETRY_DISABLED));
        handler.handle(delivery());
        verify(repository).complete(10, DeviceDownlinkStatus.FAILED, IotFailureCode.RETRY_DISABLED.code(), NOW);
        verify(repository, never()).beginDispatch(anyLong(), anyInt());
        verifyNoInteractions(router, scheduler);
    }

    @SuppressWarnings("unchecked")
    private DeviceDispatchHandler handler(DeviceDispatchDecision decision) {
        var message = mock(DeviceDownlink.class);
        when(message.messageId()).thenReturn(10L);
        when(message.deviceId()).thenReturn(20L);
        when(message.status()).thenReturn(DeviceDownlinkStatus.PENDING);
        when(message.expiresAt()).thenReturn(Optional.empty());
        when(repository.find(10)).thenReturn(Optional.of(message));
        DistributedLock lock = new DistributedLock() {
            @Override
            public <T> T execute(LockKey key, Duration wait, Supplier<T> task) {
                return task.get();
            }
        };
        return new DeviceDispatchHandler(
                repository,
                router,
                scheduler,
                Clock.fixed(NOW, ZoneOffset.UTC),
                changes,
                lock,
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                mock(MessageCodec.class),
                (value, attempt, now) -> decision);
    }

    private static IncomingMessage<DeviceDispatchMessage> delivery() {
        return new IncomingMessage<>(Id.of(1), new DeviceDispatchMessage(10, 1), new Name("TEST"), NOW, false, 1);
    }
}
