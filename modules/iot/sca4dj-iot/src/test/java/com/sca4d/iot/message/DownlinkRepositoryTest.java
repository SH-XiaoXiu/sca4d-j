package com.sca4d.iot.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.RetryPolicy;
import com.sca4d.iot.message.policy.DefaultCommandRetryPolicy;
import com.sca4d.iot.message.policy.DeviceResultAcknowledgementPolicy;
import com.sca4d.iot.message.policy.NoAcknowledgementPolicy;
import com.sca4d.iot.message.policy.NoRetryPolicy;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.persistence.EncodedDownlinkMessage;
import com.sca4d.iot.persistence.mapper.DesiredPropertyMapper;
import com.sca4d.iot.persistence.mapper.DeviceDownlinkMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

/** 验证下行 Repository 去重、分片查询并返回 `DeviceDownlink`。 */
class DownlinkRepositoryTest {

    private final DeviceDownlinkMapper messages = mock(DeviceDownlinkMapper.class);

    private final ThingDataJsonCodec json = mock(ThingDataJsonCodec.class);

    private final DevicePolicyCatalog policies = mock(DevicePolicyCatalog.class);

    private final DownlinkRepository repository = new DownlinkRepository(
            messages,
            mock(DesiredPropertyMapper.class),
            json,
            policies,
            new SqlBatches(new SqlBatchProperties()));

    @Test
    void skipsMapperForEmptyMessageIds() {
        assertThat(repository.findAll(List.of())).isEmpty();
        verifyNoInteractions(messages);
    }

    @Test
    void readsUniqueMessageIdsInOneQuery() {
        EncodedDownlinkMessage row = new EncodedDownlinkMessage(
                11L,
                21L,
                1L,
                DeviceDownlinkKind.COMMAND,
                "query",
                null,
                null,
                null,
                "{}",
                DeviceResultAcknowledgementPolicy.CODE.value(),
                30_000L,
                DefaultCommandRetryPolicy.CODE.value(),
                DeviceDownlinkStatus.PENDING,
                0,
                null,
                Instant.EPOCH);
        AcknowledgementPolicy acknowledgement = mock(AcknowledgementPolicy.class);
        RetryPolicy retry = mock(RetryPolicy.class);
        when(messages.selectDownlinkMessages(List.of(12L, 11L))).thenReturn(List.of(row));
        when(json.decodeMap("{}")).thenReturn(Map.of());
        when(policies.acknowledgement(DeviceResultAcknowledgementPolicy.CODE.value())).thenReturn(acknowledgement);
        when(policies.retry(DefaultCommandRetryPolicy.CODE.value())).thenReturn(retry);

        Map<Long, DeviceDownlink> result = repository.findAll(List.of(12L, 11L, 12L));

        assertThat(result).containsOnlyKeys(11L);
        assertThat(result.get(11L).deviceId()).isEqualTo(21L);
        verify(messages).selectDownlinkMessages(List.of(12L, 11L));
    }

    @Test
    void cancellationPreventsPublishingAndRetryWithoutHidingOtherConflicts() {
        EncodedDownlinkMessage cancelled = new EncodedDownlinkMessage(
                11L,
                21L,
                1L,
                DeviceDownlinkKind.COMMAND,
                "query",
                null,
                null,
                null,
                "{}",
                NoAcknowledgementPolicy.CODE.value(),
                1000L,
                NoRetryPolicy.CODE.value(),
                DeviceDownlinkStatus.CANCELLED,
                1,
                null,
                Instant.EPOCH);
        when(messages.selectDownlinkMessages(List.of(11L))).thenReturn(List.of(cancelled));
        when(json.decodeMap("{}")).thenReturn(Map.of());
        when(policies.acknowledgement(NoAcknowledgementPolicy.CODE.value())).thenReturn(new NoAcknowledgementPolicy());
        when(policies.retry(NoRetryPolicy.CODE.value())).thenReturn(new NoRetryPolicy());
        assertThat(repository.markPublished(11L, 1, DeviceDownlinkStatus.WAITING_ACK, Instant.EPOCH, Instant.EPOCH))
                .isFalse();
        assertThat(repository.reschedule(11L, 1, "transport")).isFalse();
        assertThat(repository.rescheduleAfterAcknowledgement(11L, "timeout")).isFalse();
        assertThatThrownBy(() -> repository.reschedule(12L, 1, "transport"))
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void partitionsLargeMessageIdCollections() {
        List<Long> ids = LongStream.rangeClosed(1, 1_001).boxed().toList();
        when(messages.selectDownlinkMessages(ids.subList(0, 1_000))).thenReturn(List.of());
        when(messages.selectDownlinkMessages(ids.subList(1_000, 1_001))).thenReturn(List.of());

        assertThat(repository.findAll(ids)).isEmpty();

        verify(messages).selectDownlinkMessages(ids.subList(0, 1_000));
        verify(messages).selectDownlinkMessages(ids.subList(1_000, 1_001));
    }
}
