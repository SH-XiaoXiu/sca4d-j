package com.sca4d.iot.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import com.sca4d.iot.api.message.AcknowledgementPolicy;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.api.message.RetryPolicy;
import com.sca4d.iot.api.schedule.DeviceSchedule;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleTrigger;
import com.sca4d.iot.api.schedule.DeviceScheduledCommand;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.config.IotProperties;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DeviceCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 验证设备定时先取得 Redis 锁，再在事务内推进最新结果并发布完整历史事件。 */
@SuppressWarnings("unchecked")
class DefaultDeviceScheduleServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00.100Z");

    private static final Instant PLANNED_AT = Instant.parse("2026-08-05T10:00:00Z");

    private static final DeviceScheduledCommand COMMAND = new DeviceScheduledCommand("power", Map.of("on", true));

    private final DeviceScheduleRepository schedules = mock(DeviceScheduleRepository.class);

    private final DeviceRepository devices = mock(DeviceRepository.class);

    private final DeviceCommandService commands = mock(DeviceCommandService.class);

    private final MessageBus messages = mock(MessageBus.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DeviceHistoryStore> history = mock(ObjectProvider.class);

    private final DistributedLock distributedLock = mock(DistributedLock.class);

    private final TransactionTemplate transactions = mock(TransactionTemplate.class);

    private final DefaultDeviceScheduleService service = new DefaultDeviceScheduleService(
            schedules,
            devices,
            commands,
            mock(IdGenerator.class),
            Clock.fixed(NOW, ZoneOffset.UTC),
            mock(DeviceChangeNotifier.class),
            new IotProperties(),
            messages,
            history,
            distributedLock,
            transactions);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void executeLockAndTransactionCallbacks() {
        when(distributedLock.execute(any(LockKey.class), any(Duration.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        when(transactions.execute(any()))
                .thenAnswer(
                        invocation -> invocation.<TransactionCallback<?>>getArgument(
                                0)
                                .doInTransaction(mock(TransactionStatus.class)));
        doAnswer(invocation -> {
            invocation
                    .<Consumer<TransactionStatus>>getArgument(0)
                    .accept(mock(TransactionStatus.class));
            return null;
        })
                .when(transactions)
                .executeWithoutResult(any());
    }

    @Test
    @DisplayName("没有到期规则时不生成下行或事件")
    void stopsWhenNoScheduleIsDue() {
        when(schedules.findNextDue(NOW)).thenReturn(Optional.empty());

        assertThat(service.triggerNextDueSchedule()).isFalse();

        verifyNoInteractions(commands, messages);
    }

    @Test
    @DisplayName("到期单次规则原子保存最新结果并发布执行事件")
    void recordsDueScheduleAndPublishesHistoryEvent() {
        DeviceSchedule due = new DeviceSchedule(
                10,
                20,
                COMMAND,
                DeviceScheduleTrigger.once(PLANNED_AT),
                Duration.ofSeconds(30),
                DeviceScheduleStatus.ACTIVE,
                PLANNED_AT,
                3);
        DeviceDownlink downlink = new DeviceDownlink(
                200,
                20,
                4,
                DeviceDownlinkKind.COMMAND,
                "power",
                null,
                10L,
                PLANNED_AT,
                Map.of("on", true),
                mock(AcknowledgementPolicy.class),
                Duration.ofSeconds(30),
                mock(RetryPolicy.class),
                DeviceDownlinkStatus.PENDING,
                0,
                PLANNED_AT.plusSeconds(30),
                NOW);
        when(schedules.findNextDue(NOW)).thenReturn(Optional.of(due));
        when(schedules.find(10)).thenReturn(Optional.of(due));
        when(
                commands.submitScheduled(
                        20,
                        "power",
                        COMMAND.inputs(),
                        PLANNED_AT.plusSeconds(30),
                        Duration.ofSeconds(30),
                        10,
                        PLANNED_AT))
                .thenReturn(downlink);
        when(
                schedules.recordExecutionAndAdvance(
                        10,
                        3,
                        DeviceScheduleStatus.COMPLETED,
                        null,
                        PLANNED_AT,
                        NOW,
                        DeviceScheduleExecutionStatus.ENQUEUED,
                        200L,
                        null))
                .thenReturn(true);
        when(devices.find(20)).thenReturn(Optional.of(device()));

        assertThat(service.triggerNextDueSchedule()).isTrue();

        verify(schedules).recordExecutionAndAdvance(
                10,
                3,
                DeviceScheduleStatus.COMPLETED,
                null,
                PLANNED_AT,
                NOW,
                DeviceScheduleExecutionStatus.ENQUEUED,
                200L,
                null);
        verify(messages).publishReliably(
                argThat(
                        event -> IotMessageTypes.DEVICE_EVENT_OCCURRED.equals(event.type())
                                && "schedule-execution:10:2026-08-05T10:00:00Z".equals(event.businessKey())));
        verify(distributedLock).execute(
                argThat(key -> new LockKey("iot", "schedule", "10").equals(key)),
                ArgumentMatchers.eq(Duration.ofSeconds(10)),
                any(Supplier.class));
    }

    /** @return 触发时用于记录绑定代数 的 Device */
    private static Device device() {
        return new Device(
                20,
                ProductBinding.code("climate"),
                "device-20",
                null,
                "Device 20",
                null,
                1,
                4,
                "test",
                null,
                Set.of());
    }
}
