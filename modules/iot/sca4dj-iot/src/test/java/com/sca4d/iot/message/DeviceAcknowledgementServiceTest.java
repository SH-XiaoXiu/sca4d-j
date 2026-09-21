package com.sca4d.iot.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.RetryPolicy;
import com.sca4d.iot.change.DeviceChangeNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** 验证迟到 ACK 不会改变已经进入最终状态的设备下行。 */
class DeviceAcknowledgementServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");

    @Test
    @DisplayName("EXPIRED 下行忽略迟到 ACK")
    @SuppressWarnings("unchecked")
    void ignoresAcknowledgementAfterExpiry() {
        DownlinkRepository downlinks = mock(DownlinkRepository.class);
        DeviceDownlink expired = new DeviceDownlink(
                10,
                20,
                1,
                DeviceDownlinkKind.COMMAND,
                "power",
                null,
                null,
                null,
                Map.of("on", true),
                mock(AcknowledgementPolicy.class),
                Duration.ofSeconds(30),
                mock(RetryPolicy.class),
                DeviceDownlinkStatus.EXPIRED,
                0,
                NOW.minusSeconds(1),
                NOW.minusSeconds(60));
        when(downlinks.find(10)).thenReturn(Optional.of(expired));
        DistributedLock distributedLock = mock(DistributedLock.class);
        when(distributedLock.execute(any(LockKey.class), any(Duration.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(2).get());
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any()))
                .thenAnswer(
                        invocation -> invocation
                                .<TransactionCallback<?>>getArgument(0)
                                .doInTransaction(mock(TransactionStatus.class)));
        DeviceAcknowledgementService service = new DeviceAcknowledgementService(
                downlinks,
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(DeviceChangeNotifier.class),
                mock(DeviceDeliveryScheduler.class),
                mock(MessageBus.class),
                distributedLock,
                transactions);
        DeviceAcknowledgement acknowledgement = new DeviceAcknowledgement(
                10,
                "success",
                Map.of(),
                "DEVICE",
                "late-ack",
                NOW,
                NOW);

        assertThat(service.acknowledge(20, acknowledgement)).isSameAs(expired);

        verify(downlinks, never()).saveLatestAcknowledgement(acknowledgement);
    }
}
