package com.sca4d.iot.schedule;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.api.schedule.DeviceSchedule;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleTrigger;
import com.sca4d.iot.api.schedule.DeviceScheduledCommand;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.persistence.EncodedDeviceSchedule;
import com.sca4d.iot.persistence.mapper.DeviceScheduleMapper;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 保存设备定时规则、下一次触发时间和最新下行生成结果。 */
public class DeviceScheduleRepository {

    private final @NotNull DeviceScheduleMapper mapper;

    private final @NotNull ThingDataJsonCodec json;

    /** 创建使用主 PostgreSQL 保存设备定时事实的 Repository。 */
    public DeviceScheduleRepository(
            @NotNull DeviceScheduleMapper mapper,
            @NotNull ThingDataJsonCodec json) {
        this.mapper = Objects.requireNonNull(mapper, "设备定时 Mapper 不能为空");
        this.json = Objects.requireNonNull(json, "设备定时 JSON Codec 不能为空");
    }

    /** 新增 ACTIVE 定时规则。 */
    @AffectedRows(target = "新增设备定时规则")
    public void add(
            long scheduleId,
            long deviceId,
            @NotNull DeviceScheduledCommand command,
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Duration validFor,
            @NotNull Instant nextFireAt) {
        DeviceScheduledCommand checkedCommand = Objects.requireNonNull(command, "设备定时 command 不能为空");
        DeviceScheduleTrigger checkedTrigger = Objects.requireNonNull(trigger, "设备定时触发规则不能为空");
        mapper.insertSchedule(
                scheduleId,
                deviceId,
                checkedCommand.commandCode(),
                json.encodeMap(checkedCommand.inputs()),
                checkedTrigger.once() ? "ONCE" : "CRON",
                checkedTrigger.executeAt().orElse(null),
                checkedTrigger.cronExpression().orElse(null),
                checkedTrigger.zoneId().map(ZoneId::getId).orElse(null),
                positiveMillis(validFor),
                Objects.requireNonNull(nextFireAt, "设备定时下一次触发时间不能为空"));
    }

    /** @return 指定设备定时规则；不存在时为空 */
    @CheckReturnValue
    public @NotNull Optional<DeviceSchedule> find(long scheduleId) {
        return mapper.selectSchedules(scheduleId, null, null, 0, 1).stream().findFirst().map(this::toSchedule);
    }

    /** @return 最早到期的一条 ACTIVE 定时规则；没有到期规则时为空 */
    @CheckReturnValue
    public @NotNull Optional<DeviceSchedule> findNextDue(@NotNull Instant now) {
        return mapper.selectSchedules(null, null, Objects.requireNonNull(now, "设备定时扫描时间不能为空"), 0, 1)
                .stream()
                .findFirst()
                .map(this::toSchedule);
    }

    /** 按乐观锁版本完整替换规则，并保存业务层已确定的目标状态。 */
    public boolean replace(
            long scheduleId,
            long expectedVersion,
            @NotNull DeviceScheduledCommand command,
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Duration validFor,
            @NotNull DeviceScheduleStatus status,
            @NotNull Instant nextFireAt) {
        DeviceScheduledCommand checkedCommand = Objects.requireNonNull(command, "设备定时 command 不能为空");
        DeviceScheduleTrigger checkedTrigger = Objects.requireNonNull(trigger, "设备定时触发规则不能为空");
        return AffectedRowCounts.changed(
                mapper.replaceSchedule(
                        scheduleId,
                        expectedVersion,
                        checkedCommand.commandCode(),
                        json.encodeMap(checkedCommand.inputs()),
                        checkedTrigger.once() ? "ONCE" : "CRON",
                        checkedTrigger.executeAt().map(Patch::set).orElseGet(Patch::clear),
                        checkedTrigger.cronExpression().map(Patch::set).orElseGet(Patch::clear),
                        checkedTrigger.zoneId().map(ZoneId::getId).map(Patch::set).orElseGet(Patch::clear),
                        positiveMillis(validFor),
                        Objects.requireNonNull(status, "设备定时目标状态不能为空"),
                        Objects.requireNonNull(nextFireAt, "设备定时下一次触发时间不能为空")),
                "替换设备定时规则");
    }

    /** 按乐观锁版本修改状态和下一次触发时间。 */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean changeStatus(
            long scheduleId,
            long expectedVersion,
            @NotNull DeviceScheduleStatus status,
            @Nullable Instant nextFireAt) {
        return AffectedRowCounts.changed(
                mapper.updateProgress(
                        scheduleId,
                        expectedVersion,
                        null,
                        Patch.set(Objects.requireNonNull(status, "设备定时状态不能为空")),
                        nextFireAt == null ? Patch.clear() : Patch.set(nextFireAt),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore()),
                "修改设备定时状态");
    }

    /** 保存最新执行结果，并在同一写入中推进状态和下一次触发时间。 */
    public boolean recordExecutionAndAdvance(
            long scheduleId,
            long expectedVersion,
            @NotNull DeviceScheduleStatus status,
            @Nullable Instant nextFireAt,
            @NotNull Instant plannedAt,
            @NotNull Instant triggeredAt,
            @NotNull DeviceScheduleExecutionStatus executionStatus,
            @Nullable Long messageId,
            @Nullable String failureCode) {
        return AffectedRowCounts.changed(
                mapper.updateProgress(
                        scheduleId,
                        expectedVersion,
                        DeviceScheduleStatus.ACTIVE,
                        Patch.set(Objects.requireNonNull(status, "设备定时推进状态不能为空")),
                        nextFireAt == null ? Patch.clear() : Patch.set(nextFireAt),
                        Patch.set(Objects.requireNonNull(plannedAt, "设备定时计划时间不能为空")),
                        Patch.set(Objects.requireNonNull(triggeredAt, "设备定时触发时间不能为空")),
                        Patch.set(Objects.requireNonNull(executionStatus, "设备定时执行状态不能为空")),
                        messageId == null ? Patch.clear() : Patch.set(messageId),
                        failureCode == null ? Patch.clear() : Patch.set(failureCode)),
                "推进设备定时执行状态");
    }

    /** @return Device 的定时规则页 */
    @CheckReturnValue
    public @NotNull Page<DeviceSchedule> findDeviceSchedules(
            long deviceId,
            @NotNull PageRequest page) {
        PageRequest checked = Objects.requireNonNull(page, "设备定时分页要求不能为空").verifyMaxSize(100);
        long total = mapper.countDeviceSchedules(deviceId);
        List<DeviceSchedule> items = total == 0
                ? List.of()
                : mapper.selectSchedules(null, deviceId, null, checked.offset(), checked.size())
                        .stream()
                        .map(this::toSchedule)
                        .toList();
        return new Page<>(items, checked, total);
    }

    /** 把持久化字段恢复成稳定定时规则。 */
    private @NotNull DeviceSchedule toSchedule(@NotNull EncodedDeviceSchedule state) {
        EncodedDeviceSchedule checked = Objects.requireNonNull(state, "设备定时持久化状态不能为空");
        DeviceScheduleTrigger trigger;
        if ("ONCE".equals(checked.triggerType())) {
            trigger = DeviceScheduleTrigger.once(
                    Objects.requireNonNull(
                            checked.executeAt(),
                            "单次设备定时执行时间不能为空"));
        } else if ("CRON".equals(checked.triggerType())) {
            trigger = DeviceScheduleTrigger.cron(
                    Objects.requireNonNull(checked.cronExpression(), "设备定时 Cron 不能为空"),
                    ZoneId.of(Objects.requireNonNull(checked.zoneId(), "设备定时时区不能为空")));
        } else {
            throw new IllegalStateException("未知设备定时触发类型: " + checked.triggerType());
        }
        return new DeviceSchedule(
                checked.scheduleId(),
                checked.deviceId(),
                new DeviceScheduledCommand(checked.commandCode(), json.decodeMap(checked.commandInputsJson())),
                trigger,
                Duration.ofMillis(checked.validForMillis()),
                checked.status(),
                checked.nextFireAt(),
                checked.version());
    }

    /** 把有效期转成数据库毫秒数，并拒绝零、负数和溢出值。 */
    private static long positiveMillis(@NotNull Duration duration) {
        Duration checked = Objects.requireNonNull(duration, "设备定时有效期不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException("设备定时有效期必须为正数");
        }
        try {
            return checked.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("设备定时有效期过大", exception);
        }
    }

}
