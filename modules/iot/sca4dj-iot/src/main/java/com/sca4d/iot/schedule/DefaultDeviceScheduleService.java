package com.sca4d.iot.schedule;

import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.api.schedule.DeviceSchedule;
import com.sca4d.iot.api.schedule.DeviceScheduleExecution;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleQueryService;
import com.sca4d.iot.api.schedule.DeviceScheduleService;
import com.sca4d.iot.api.schedule.DeviceScheduleStatus;
import com.sca4d.iot.api.schedule.DeviceScheduleTrigger;
import com.sca4d.iot.api.schedule.DeviceScheduledCommand;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.config.IotProperties;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DeviceCommandService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Objects;

/** 管理 PostgreSQL 权威设备定时规则，并把每次执行发布成统一 IoT 事件。 */
public class DefaultDeviceScheduleService implements DeviceScheduleService, DeviceScheduleQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceScheduleService.class);

    private static final int MAX_FAILURE_CODE_LENGTH = 128;

    private static final Duration LOCK_WAIT = Duration.ofSeconds(10);

    private final @NotNull DeviceScheduleRepository schedules;

    private final @NotNull DeviceRepository devices;

    private final @NotNull DeviceCommandService commands;

    private final @NotNull IdGenerator ids;

    private final @NotNull Clock clock;

    private final @NotNull DeviceChangeNotifier changes;

    private final @NotNull IotProperties properties;

    private final @NotNull MessageBus messages;

    private final @NotNull ObjectProvider<DeviceHistoryStore> history;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /** 创建从 PostgreSQL 扫描到期规则，并使用 Redis 锁互斥推进同一规则的服务。 */
    @SuppressWarnings("ParameterNumber")
    public DefaultDeviceScheduleService(
            @NotNull DeviceScheduleRepository schedules,
            @NotNull DeviceRepository devices,
            @NotNull DeviceCommandService commands,
            @NotNull IdGenerator ids,
            @NotNull Clock clock,
            @NotNull DeviceChangeNotifier changes,
            @NotNull IotProperties properties,
            @NotNull MessageBus messages,
            @NotNull ObjectProvider<DeviceHistoryStore> history,
            @NotNull DistributedLock distributedLock,
            @NotNull TransactionTemplate transactions) {
        this.schedules = Objects.requireNonNull(schedules, "设备定时 Repository 不能为空");
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.commands = Objects.requireNonNull(commands, "Device command 服务不能为空");
        this.ids = Objects.requireNonNull(ids, "设备定时 ID 生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "设备定时时钟不能为空");
        this.changes = Objects.requireNonNull(changes, "Device 变化通知器不能为空");
        this.properties = Objects.requireNonNull(properties, "IoT 配置不能为空");
        this.messages = Objects.requireNonNull(messages, "MessageBus 不能为空");
        this.history = Objects.requireNonNull(history, "DeviceHistoryStore Provider 不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "设备定时分布式锁不能为空");
        this.transactions = Objects.requireNonNull(transactions, "设备定时事务模板不能为空");
    }

    @Override
    @Transactional
    @CheckReturnValue
    public @NotNull DeviceSchedule create(
            long deviceId,
            @NotNull DeviceScheduledCommand command,
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Duration validFor) {
        validateDeviceId(deviceId);
        Instant now = clock.instant();
        DeviceScheduleTrigger checkedTrigger = validatedTrigger(trigger);
        long scheduleId = ids.nextId().value();
        schedules.add(
                scheduleId,
                deviceId,
                Objects.requireNonNull(command, "设备定时 command 不能为空"),
                checkedTrigger,
                validFor,
                futureFireAt(checkedTrigger, now));
        DeviceSchedule created = loadById(scheduleId);
        changes.changed(deviceId, DeviceChangeArea.SCHEDULE, now);
        LOG.debug("设备定时规则已创建 | scheduleId={} deviceId={}", scheduleId, deviceId);
        return created;
    }

    @Override
    @Transactional
    @CheckReturnValue
    public @NotNull DeviceSchedule replace(
            long scheduleId,
            long expectedVersion,
            @NotNull DeviceScheduledCommand command,
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Duration validFor) {
        Instant now = clock.instant();
        DeviceScheduleTrigger checkedTrigger = validatedTrigger(trigger);
        if (!schedules.replace(
                scheduleId,
                expectedVersion,
                command,
                checkedTrigger,
                validFor,
                DeviceScheduleStatus.ACTIVE,
                futureFireAt(checkedTrigger, now))) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        DeviceSchedule replaced = loadById(scheduleId);
        changes.changed(replaced.deviceId(), DeviceChangeArea.SCHEDULE, now);
        return replaced;
    }

    @Override
    @Transactional
    @CheckReturnValue
    public @NotNull DeviceSchedule changeStatus(
            long scheduleId,
            long expectedVersion,
            @NotNull DeviceScheduleStatus status) {
        DeviceScheduleStatus target = Objects.requireNonNull(status, "设备定时目标状态不能为空");
        if (target != DeviceScheduleStatus.ACTIVE && target != DeviceScheduleStatus.PAUSED) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        DeviceSchedule current = schedules.find(scheduleId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.STATE_CONFLICT));
        if (current.version() != expectedVersion
                || current.status() == DeviceScheduleStatus.CANCELLED
                || current.status() == DeviceScheduleStatus.COMPLETED) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        if (current.status() == target) {
            return current;
        }
        Instant now = clock.instant();
        Instant nextFireAt = target == DeviceScheduleStatus.ACTIVE
                ? nextFireAt(current.trigger(), now)
                : null;
        if (!schedules.changeStatus(scheduleId, expectedVersion, target, nextFireAt)) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        DeviceSchedule changed = loadById(scheduleId);
        changes.changed(changed.deviceId(), DeviceChangeArea.SCHEDULE, now);
        return changed;
    }

    @Override
    @Transactional
    public void cancel(long scheduleId, long expectedVersion) {
        DeviceSchedule current = schedules.find(scheduleId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.STATE_CONFLICT));
        if (current.version() != expectedVersion || current.status() == DeviceScheduleStatus.CANCELLED) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        Instant now = clock.instant();
        if (!schedules.changeStatus(scheduleId, expectedVersion, DeviceScheduleStatus.CANCELLED, null)) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        changes.changed(current.deviceId(), DeviceChangeArea.SCHEDULE, now);
    }

    /** 按计划标识读取设备计划，并在不存在时抛出稳定的业务错误。 */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull DeviceSchedule loadById(long scheduleId) {
        return schedules.find(scheduleId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.STATE_CONFLICT));
    }

    @Override
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<DeviceSchedule> schedules(long deviceId, @NotNull PageRequest page) {
        validateDeviceId(deviceId);
        return schedules.findDeviceSchedules(deviceId, page);
    }

    @Override
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Page<DeviceScheduleExecution> executions(long scheduleId, @NotNull PageRequest page) {
        DeviceSchedule schedule = loadById(scheduleId);
        DeviceHistoryStore store = history.getIfAvailable();
        if (store == null) {
            throw new BusinessException(IotErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        return store.scheduleExecutions(schedule.scheduleId(), page);
    }

    /**
     * 读取一条到期候选，取得候选规则的 Redis 锁后在事务内重新确认并生成 command；没有到期候选时返回 `false`。
     *
     * @throws DeviceScheduleTriggerFailure command 业务校验失败时抛出，调用方必须在新事务中暂停候选规则
     */
    public boolean triggerNextDueSchedule() {
        Instant scannedAt = clock.instant();
        DeviceSchedule candidate = schedules.findNextDue(scannedAt).orElse(null);
        if (candidate == null) {
            return false;
        }
        distributedLock.execute(
                scheduleLock(candidate.scheduleId()),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> triggerDueCandidate(candidate));
                    return null;
                });
        return true;
    }

    /** 在规则锁内重新确认候选版本和计划时间，并推进一次到期执行。 */
    private void triggerDueCandidate(@NotNull DeviceSchedule candidate) {
        // 锁内重新读取规则，只有版本、状态和计划时间都未变化时才继续执行，避免重复触发旧候选。
        DeviceSchedule schedule = schedules.find(candidate.scheduleId()).orElse(null);
        Instant now = clock.instant();
        if (schedule == null
                || schedule.version() != candidate.version()
                || schedule.status() != DeviceScheduleStatus.ACTIVE
                || !schedule.nextFireAt().equals(candidate.nextFireAt())
                || schedule.nextFireAt().filter(fireAt -> !fireAt.isAfter(now)).isEmpty()) {
            return;
        }
        Instant plannedAt = schedule.nextFireAt().orElseThrow();
        Instant expiresAt = addValidFor(plannedAt, schedule.validFor());
        DeviceScheduleExecutionStatus executionStatus;
        Long messageId = null;

        // 过期规则只记录 EXPIRED；仍在有效期内的规则提交下行消息，业务校验失败交由外层暂停规则。
        if (!now.isBefore(expiresAt)) {
            executionStatus = DeviceScheduleExecutionStatus.EXPIRED;
        } else {
            try {
                DeviceDownlink message = commands.submitScheduled(
                        schedule.deviceId(),
                        schedule.command().commandCode(),
                        schedule.command().inputs(),
                        expiresAt,
                        properties.getAcknowledgementTimeout(),
                        schedule.scheduleId(),
                        plannedAt);
                executionStatus = DeviceScheduleExecutionStatus.ENQUEUED;
                messageId = message.messageId();
            } catch (BusinessException exception) {
                throw new DeviceScheduleTriggerFailure(
                        schedule.scheduleId(),
                        schedule.version(),
                        plannedAt,
                        exception.errorCode().code(),
                        exception);
            }
        }
        DeviceScheduleStatus nextStatus = schedule.trigger().once()
                ? DeviceScheduleStatus.COMPLETED
                : DeviceScheduleStatus.ACTIVE;
        Instant nextFireAt = schedule.trigger().once() ? null : nextCronFire(schedule.trigger(), now);

        // 执行结果、下一状态和下一次触发时间必须在同一事务中写入，保证规则不会重复领取。
        recordExecution(
                schedule,
                plannedAt,
                now,
                executionStatus,
                messageId,
                null,
                nextStatus,
                nextFireAt);
    }

    /** 在触发事务回滚后，取得同一规则的 Redis 锁，并在新事务中保存同版本 FAILED 结果。 */
    public void pauseFailedSchedule(@NotNull DeviceScheduleTriggerFailure failure) {
        DeviceScheduleTriggerFailure checked = Objects.requireNonNull(failure, "设备定时触发失败不能为空");
        distributedLock.execute(
                scheduleLock(checked.scheduleId()),
                LOCK_WAIT,
                () -> {
                    transactions.executeWithoutResult(status -> pauseFailedScheduleInTransaction(checked));
                    return null;
                });
    }

    /** 在规则锁内确认失败对应的版本和计划时间仍未变化。 */
    private void pauseFailedScheduleInTransaction(@NotNull DeviceScheduleTriggerFailure failure) {
        DeviceSchedule schedule = schedules.find(failure.scheduleId()).orElse(null);
        if (schedule == null
                || schedule.version() != failure.scheduleVersion()
                || schedule.status() != DeviceScheduleStatus.ACTIVE
                || schedule.nextFireAt().filter(failure.plannedAt()::equals).isEmpty()) {
            return;
        }
        recordExecution(
                schedule,
                failure.plannedAt(),
                clock.instant(),
                DeviceScheduleExecutionStatus.FAILED,
                null,
                safeFailureCode(failure.failureCode()),
                DeviceScheduleStatus.PAUSED,
                null);
    }

    /** @return 指定设备定时规则在全部应用实例间共用的锁键 */
    private static @NotNull LockKey scheduleLock(long scheduleId) {
        return new LockKey(TreeCode.of("IOT:SCHEDULE"), scheduleId);
    }

    /** 原子保存最新结果、推进规则，并可靠发布完整执行历史事件。 */
    private void recordExecution(
            @NotNull DeviceSchedule schedule,
            @NotNull Instant plannedAt,
            @NotNull Instant triggeredAt,
            @NotNull DeviceScheduleExecutionStatus executionStatus,
            @Nullable Long messageId,
            @Nullable String failureCode,
            @NotNull DeviceScheduleStatus nextStatus,
            @Nullable Instant nextFireAt) {
        if (!schedules.recordExecutionAndAdvance(
                schedule.scheduleId(),
                schedule.version(),
                nextStatus,
                nextFireAt,
                plannedAt,
                triggeredAt,
                executionStatus,
                messageId,
                failureCode)) {
            throw new IllegalStateException("推进设备定时规则时发生并发修改");
        }
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("scheduleId", schedule.scheduleId());
        data.put("plannedAt", plannedAt.toString());
        data.put("triggeredAt", triggeredAt.toString());
        data.put("status", executionStatus.name());
        if (messageId != null) {
            data.put("messageId", messageId);
        }
        if (failureCode != null) {
            data.put("failureCode", failureCode);
        }
        String sourceEventId = schedule.scheduleId() + ":" + plannedAt;
        Device device = devices.find(schedule.deviceId())
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
        DeviceEventMessage payload = new DeviceEventMessage(
                schedule.deviceId(),
                device.bindingGeneration(),
                "$schedule.execution",
                sourceEventId,
                data,
                plannedAt,
                true,
                triggeredAt);
        Id eventMessageId = messages.dispatch(
                new Event<>(
                        IotMessageTypes.DEVICE_EVENT_OCCURRED,
                        "schedule-execution:" + sourceEventId,
                        plannedAt,
                        payload),
                DispatchPolicy.outbox()).messageId();
        changes.changed(schedule.deviceId(), DeviceChangeArea.SCHEDULE, triggeredAt);
        LOG.debug(
                "设备定时执行结果已保存 | scheduleId={} plannedAt={} status={} messageId={} eventMessageId={}",
                schedule.scheduleId(),
                plannedAt,
                executionStatus,
                messageId,
                eventMessageId);
    }

    /** 校验 Device 存在。 */
    private void validateDeviceId(long deviceId) {
        if (devices.find(deviceId).isEmpty()) {
            throw new BusinessException(IotErrorCode.DEVICE_NOT_FOUND);
        }
    }

    /** 解析 Cron 并返回可继续使用的触发规则。 */
    private static @NotNull DeviceScheduleTrigger validatedTrigger(@NotNull DeviceScheduleTrigger trigger) {
        DeviceScheduleTrigger checked = Objects.requireNonNull(trigger, "设备定时触发规则不能为空");
        checked.cronExpression().ifPresent(expression -> {
            try {
                CronExpression.parse(expression);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
            }
        });
        return checked;
    }

    /** 创建或替换规则时要求下一次触发严格晚于当前时间。 */
    private static @NotNull Instant futureFireAt(
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Instant now) {
        Instant next = trigger.executeAt().orElseGet(() -> nextCronFire(trigger, now));
        if (!next.isAfter(now)) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        return next;
    }

    /** 恢复暂停规则时保留单次原计划时间，Cron 从当前时间计算下一次。 */
    private static @NotNull Instant nextFireAt(
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Instant now) {
        return trigger.executeAt().orElseGet(() -> nextCronFire(trigger, now));
    }

    /** 计算严格晚于给定时间的下一次带时区 Cron 触发时间。 */
    private static @NotNull Instant nextCronFire(
            @NotNull DeviceScheduleTrigger trigger,
            @NotNull Instant after) {
        String expression = trigger.cronExpression()
                .orElseThrow(() -> new IllegalArgumentException("Cron 设备定时缺少表达式"));
        ZonedDateTime zonedAfter = Objects.requireNonNull(after, "Cron 计算起点不能为空")
                .atZone(trigger.zoneId().orElseThrow());
        try {
            ZonedDateTime next = CronExpression.parse(expression).next(zonedAfter);
            if (next == null) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT);
            }
            return next.toInstant();
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
    }

    /** 计算当前计划生成下行的绝对截止时间。 */
    private static @NotNull Instant addValidFor(
            @NotNull Instant plannedAt,
            @NotNull Duration validFor) {
        try {
            return Objects.requireNonNull(plannedAt, "设备定时计划时间不能为空")
                    .plus(Objects.requireNonNull(validFor, "设备定时有效期不能为空"));
        } catch (DateTimeException | ArithmeticException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
    }

    /** 把可公开记录的失败码限制在数据库列长度内。 */
    private static @NotNull String safeFailureCode(@NotNull String failureCode) {
        String checked = Objects.requireNonNull(failureCode, "设备定时失败 code 不能为空").trim();
        if (checked.isEmpty()) {
            checked = IotErrorCode.INVALID_INPUT.code();
        }
        return checked.length() <= MAX_FAILURE_CODE_LENGTH
                ? checked
                : checked.substring(0, MAX_FAILURE_CODE_LENGTH);
    }
}
