package com.sca4d.iot.message;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.query.DesiredStateSnapshot;
import com.sca4d.iot.model.ThingDataJsonCodec;
import com.sca4d.iot.persistence.EncodedDeviceAcknowledgement;
import com.sca4d.iot.persistence.EncodedDownlinkMessage;
import com.sca4d.iot.persistence.mapper.DesiredPropertyMapper;
import com.sca4d.iot.persistence.mapper.DeviceDownlinkMapper;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 保存 desired state、标准下行业务状态和每条消息的最新 ACK。 */
public class DownlinkRepository {

    private static final Set<DeviceDownlinkStatus> ACTIVE_STATUSES = Set.of(
            DeviceDownlinkStatus.PENDING,
            DeviceDownlinkStatus.SENDING,
            DeviceDownlinkStatus.WAITING_ACK);

    private final @NotNull DeviceDownlinkMapper messages;

    private final @NotNull DesiredPropertyMapper desired;

    private final @NotNull ThingDataJsonCodec json;

    private final @NotNull DevicePolicyCatalog policies;

    private final @NotNull SqlBatches sqlBatches;

    /**
     * @param messages 下行消息 SQL Mapper
     * @param desired desired property SQL Mapper
     * @param json 物模型数据 JSON 编解码器
     * @param policies 具体应用选择的设备策略目录
     * @param sqlBatches SQL 批处理器
     */
    public DownlinkRepository(
            @NotNull DeviceDownlinkMapper messages,
            @NotNull DesiredPropertyMapper desired,
            @NotNull ThingDataJsonCodec json,
            @NotNull DevicePolicyCatalog policies,
            @NotNull SqlBatches sqlBatches) {
        this.messages = Objects.requireNonNull(messages, "下行消息 Mapper 不能为空");
        this.desired = Objects.requireNonNull(desired, "desired property Mapper 不能为空");
        this.json = Objects.requireNonNull(json, "物模型数据 JSON 编解码器不能为空");
        this.policies = Objects.requireNonNull(policies, "设备策略目录不能为空");
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** @return 当前绑定存在尚未结束的 desired 或 command 时为 {@code true} */
    @CheckReturnValue
    public boolean hasActiveMessages(long deviceId, long bindingGeneration) {
        return messages.countActiveDownlinkMessages(deviceId, bindingGeneration) > 0;
    }

    /** 读取 Device 当前 desired sequence；Device 不存在时返回 {@code null}。 */
    public @Nullable Long findDesiredSequence(long deviceId) {
        return desired.selectDeviceDesiredSequence(deviceId);
    }

    /** 批量读取指定编码的 desired property，并为不存在的编码返回空值。 */
    public @NotNull Map<@NotNull String, @NotNull Optional<DesiredPropertyState>> findDesiredProperties(
            long deviceId,
            long bindingGeneration,
            @NotNull Collection<@NotNull String> propertyCodes) {
        List<String> codes = List.copyOf(Objects.requireNonNull(propertyCodes, "desired property code 不能为空"));
        LinkedHashMap<String, Optional<DesiredPropertyState>> result = new LinkedHashMap<>();
        codes.forEach(code -> result.put(Objects.requireNonNull(code, "desired property code 不能为空"), Optional.empty()));
        for (var value : sqlBatches.query(
                codes,
                1,
                2,
                batch -> desired.select(deviceId, bindingGeneration, batch))) {
            result.put(
                    value.propertyCode(),
                    Optional.of(
                            new DesiredPropertyState(
                                    value.propertyCode(),
                                    value.desiredSequence(),
                                    json.decodeValue(value.valueJson()),
                                    value.convergedAt())));
        }
        return Collections.unmodifiableMap(result);
    }

    /** 当 desired sequence 仍等于预期值时更新它。 */
    public boolean advanceDesiredSequence(
            long deviceId,
            long expectedDesiredSequence,
            long desiredSequence) {
        return AffectedRowCounts.changed(
                desired.updateDeviceDesiredSequence(
                        deviceId,
                        expectedDesiredSequence,
                        desiredSequence),
                "推进 Device desired sequence");
    }

    /** 保存一个已经规范化的 desired property。 */
    @AffectedRows(target = "保存 desired property")
    public void saveDesiredProperty(
            long deviceId,
            long bindingGeneration,
            @NotNull String propertyCode,
            long desiredSequence,
            @NotNull Object value) {
        desired.upsert(
                deviceId,
                bindingGeneration,
                propertyCode,
                desiredSequence,
                json.encodeValue(value));
    }

    /** 删除一个当前 desired property。 */
    @AffectedRows(target = "清除 desired property")
    public void deleteDesiredProperty(
            long deviceId,
            long bindingGeneration,
            @NotNull String propertyCode) {
        desired.delete(deviceId, bindingGeneration, propertyCode);
    }

    /** 删除 Device 旧绑定的全部 desired 当前投影。 */
    public int deleteDesiredState(long deviceId, long bindingGeneration) {
        return desired.deleteBinding(deviceId, bindingGeneration);
    }

    /** 将指定属性尚未结束的 desired 消息改为给定状态。 */
    public void finishPreviousDesiredMessages(
            long deviceId,
            long bindingGeneration,
            @NotNull String propertyCode,
            @NotNull DeviceDownlinkStatus status,
            @NotNull Instant now) {
        int changed = messages.updateProgress(
                null,
                deviceId,
                bindingGeneration,
                DeviceDownlinkKind.DESIRED,
                propertyCode,
                ACTIVE_STATUSES,
                null,
                Patch.set(Objects.requireNonNull(status, "下行状态不能为空")),
                Patch.ignore(),
                Patch.clear(),
                Patch.set(Objects.requireNonNull(now, "下行时间不能为空")),
                Patch.ignore());
        AffectedRowCounts.requireAtLeast(changed, 0, "完成旧 desired 下行消息");
    }

    /** 保存一条标准下行消息。 */
    @AffectedRows(target = "新增下行消息")
    public void add(@NotNull DeviceDownlink message) {
        DeviceDownlink checked = Objects.requireNonNull(message, "下行消息不能为空");
        messages.insertDownlinkMessage(
                checked.messageId(),
                checked.deviceId(),
                checked.bindingGeneration(),
                checked.kind(),
                checked.targetCode(),
                checked.desiredSequence().isPresent() ? checked.desiredSequence().getAsLong() : null,
                checked.scheduleId().isPresent() ? checked.scheduleId().getAsLong() : null,
                checked.scheduledAt().orElse(null),
                json.encodeMap(checked.data()),
                checked.acknowledgementPolicy().code(),
                checked.acknowledgementTimeout().toMillis(),
                checked.retryPolicy().code(),
                checked.expiresAt().orElse(null));
    }

    /** 按标识查询标准下行消息。 */
    @CheckReturnValue
    public @NotNull Optional<DeviceDownlink> find(long messageId) {
        return messages.selectDownlinkMessages(List.of(messageId)).stream().findFirst().map(this::toMessage);
    }

    /** 分页读取主库保存的指定下行 ACK 投影；主库最多返回一条。 */
    @CheckReturnValue
    public @NotNull Page<DeviceAcknowledgement> findAcknowledgementsByMessageId(
            long messageId,
            @NotNull PageRequest page) {
        if (messageId <= 0) {
            throw new IllegalArgumentException("下行消息标识必须为正数");
        }
        PageRequest checked = Objects.requireNonNull(page, "业务回执分页要求不能为空").verifyMaxSize(100);
        if (checked.page() != 1) {
            return Page.empty(checked);
        }
        EncodedDeviceAcknowledgement row = messages.selectAcknowledgementByMessageId(messageId);
        return row == null
                ? Page.empty(checked)
                : new Page<>(List.of(toAcknowledgement(row)), checked, 1);
    }

    /**
     * 批量读取指定标识的下行消息。
     *
     * @param messageIds 全局下行消息标识；重复标识只查询一次
     * @return 不可修改映射；不存在的消息不进入结果
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull DeviceDownlink> findAll(
            @NotNull Collection<@NotNull Long> messageIds) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        Objects.requireNonNull(messageIds, "下行消息标识不能为空")
                .forEach(id -> uniqueIds.add(Objects.requireNonNull(id, "下行消息标识不能为空")));
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Long, DeviceDownlink> result = new LinkedHashMap<>();
        sqlBatches.query(uniqueIds, 1, 0, messages::selectDownlinkMessages)
                .stream()
                .map(this::toMessage)
                .forEach(message -> result.put(message.messageId(), message));
        return Collections.unmodifiableMap(result);
    }

    /** 读取 Device 当前绑定的全部 desired 属性。 */
    @CheckReturnValue
    public @NotNull List<DesiredStateSnapshot> findDesired(long deviceId, long bindingGeneration) {
        return desired.select(deviceId, bindingGeneration, null)
                .stream()
                .map(
                        row -> new DesiredStateSnapshot(
                                row.propertyCode(),
                                row.desiredSequence(),
                                json.decodeValue(row.valueJson()),
                                row.convergedAt()))
                .toList();
    }

    /** 分页读取 Device 下行消息。 */
    @CheckReturnValue
    public @NotNull Page<DeviceDownlink> findDownlinks(
            long deviceId,
            @NotNull PageRequest page) {
        PageRequest checked = Objects.requireNonNull(page, "下行分页要求不能为空").verifyMaxSize(100);
        long total = messages.countDeviceDownlinks(deviceId);
        List<DeviceDownlink> items = total == 0
                ? List.of()
                : messages.selectDeviceDownlinks(deviceId, checked.offset(), checked.size())
                        .stream()
                        .map(this::toMessage)
                        .toList();
        return new Page<>(items, checked, total);
    }

    /** 分页读取 Device 下行消息收到的业务回执。 */
    @CheckReturnValue
    public @NotNull Page<DeviceAcknowledgement> findAcknowledgements(
            long deviceId,
            @NotNull PageRequest page) {
        PageRequest checked = Objects.requireNonNull(page, "业务回执分页要求不能为空").verifyMaxSize(100);
        long total = messages.countLatestAcknowledgements(deviceId);
        List<DeviceAcknowledgement> items = total == 0
                ? List.of()
                : messages.selectLatestAcknowledgements(deviceId, checked.offset(), checked.size())
                        .stream()
                        .map(this::toAcknowledgement)
                        .toList();
        return new Page<>(items, checked, total);
    }

    /** 把当前 PENDING 消息转换成指定发送尝试；状态或尝试次数变化时返回 false。 */
    public boolean beginDispatch(long messageId, int attempt) {
        return AffectedRowCounts.changed(
                messages.updateProgress(
                        messageId,
                        null,
                        null,
                        null,
                        null,
                        Set.of(DeviceDownlinkStatus.PENDING),
                        attempt - 1,
                        Patch.set(DeviceDownlinkStatus.SENDING),
                        Patch.set(attempt),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore()),
                "开始下行发送");
    }

    /** 协议发布成功后写入状态；并发取消时返回 false，其他状态或尝试冲突仍抛出乐观锁异常。 */
    @CheckReturnValue
    public boolean markPublished(
            long messageId,
            int attempt,
            @NotNull DeviceDownlinkStatus status,
            @Nullable Instant acknowledgementDeadline,
            @NotNull Instant now) {
        int changed = messages.updateProgress(
                messageId,
                null,
                null,
                null,
                null,
                Set.of(DeviceDownlinkStatus.SENDING),
                attempt,
                Patch.set(status),
                Patch.ignore(),
                acknowledgementDeadline == null ? Patch.clear() : Patch.set(acknowledgementDeadline),
                status == DeviceDownlinkStatus.SUCCEEDED ? Patch.set(now) : Patch.clear(),
                Patch.clear());
        if (changed == 0
                && find(messageId).filter(message -> message.status() == DeviceDownlinkStatus.CANCELLED).isPresent()) {
            return false;
        }
        AffectedRowCounts.requireSingle(changed, ZeroRowPolicy.OPTIMISTIC_LOCK, "下行 publish 状态");
        return true;
    }

    /** 传输失败后恢复 PENDING；并发取消时返回 false，其他冲突仍抛出乐观锁异常。 */
    @CheckReturnValue
    public boolean reschedule(
            long messageId,
            int attempt,
            @NotNull String error) {
        int changed = messages.updateProgress(
                messageId,
                null,
                null,
                null,
                null,
                Set.of(DeviceDownlinkStatus.SENDING),
                attempt,
                Patch.set(DeviceDownlinkStatus.PENDING),
                Patch.ignore(),
                Patch.clear(),
                Patch.ignore(),
                Patch.set(error));
        if (changed == 0
                && find(messageId).filter(message -> message.status() == DeviceDownlinkStatus.CANCELLED).isPresent()) {
            return false;
        }
        AffectedRowCounts.requireSingle(changed, ZeroRowPolicy.OPTIMISTIC_LOCK, "下行消息重排队");
        return true;
    }

    /** 回执策略要求重试时重新排队；并发取消返回 false，其他冲突仍抛出乐观锁异常。 */
    @CheckReturnValue
    public boolean rescheduleAfterAcknowledgement(
            long messageId,
            @NotNull String error) {
        int changed = messages.updateProgress(
                messageId,
                null,
                null,
                null,
                null,
                ACTIVE_STATUSES,
                null,
                Patch.set(DeviceDownlinkStatus.PENDING),
                Patch.ignore(),
                Patch.clear(),
                Patch.ignore(),
                Patch.set(error));
        if (changed == 0
                && find(messageId).filter(message -> message.status() == DeviceDownlinkStatus.CANCELLED).isPresent()) {
            return false;
        }
        AffectedRowCounts.requireSingle(changed, ZeroRowPolicy.OPTIMISTIC_LOCK, "下行回执重排队");
        return true;
    }

    /** 把仍未结束的消息转换成最终状态。 */
    public boolean complete(
            long messageId,
            @NotNull DeviceDownlinkStatus status,
            @Nullable String error,
            @NotNull Instant now) {
        return AffectedRowCounts.changed(
                messages.updateProgress(
                        messageId,
                        null,
                        null,
                        null,
                        null,
                        ACTIVE_STATUSES,
                        null,
                        Patch.set(status),
                        Patch.ignore(),
                        Patch.clear(),
                        Patch.set(now),
                        error == null ? Patch.clear() : Patch.set(error)),
                "完成下行消息");
    }

    /**
     * ACK 事实标识变化时替换最新 ACK。
     *
     * @return 已保存 ACK 的稳定摘要；重复事实返回 {@code null}
     */
    public @Nullable String saveLatestAcknowledgement(@NotNull DeviceAcknowledgement acknowledgement) {
        DeviceAcknowledgement checked = Objects.requireNonNull(acknowledgement, "Device 回执不能为空");
        String identity = checked.sourceAcknowledgementId() == null
                ? checked.source() + ':' + checked.ackCode() + ':' + json.encodeMap(checked.data())
                        + ':' + checked.acknowledgedAt()
                : checked.source() + ':' + checked.sourceAcknowledgementId();
        String identityHash = DigestUtil.sha256Hex(identity);
        boolean changed = AffectedRowCounts.changed(
                messages.updateLatestAcknowledgement(
                        checked.messageId(),
                        identityHash,
                        checked.ackCode(),
                        json.encodeMap(checked.data()),
                        checked.source(),
                        checked.sourceAcknowledgementId(),
                        checked.acknowledgedAt(),
                        checked.receivedAt()),
                "保存最新 Device 回执");
        return changed ? identityHash : null;
    }

    /** 分批删除指定时间前已经进入最终状态的下行消息。 */
    public int deleteCompletedBefore(@NotNull Instant completedBefore, int batchSize) {
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("下行清理批量必须在 1 到 10000 之间");
        }
        return messages.deleteCompletedBefore(
                Objects.requireNonNull(completedBefore, "下行清理截止时间不能为空"),
                batchSize);
    }

    /** 批量标记与 reported 值相等的 desired 属性，并返回需要推导 ACK 的当前消息。 */
    @CheckReturnValue
    public @NotNull List<DesiredConvergence> convergeDesired(
            long deviceId,
            long bindingGeneration,
            @NotNull Map<@NotNull String, ?> values,
            @NotNull Instant now) {
        Map<String, ?> checkedValues = Objects.requireNonNull(values, "reported 属性值不能为空");
        if (checkedValues.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, String> encoded = new LinkedHashMap<>();
        checkedValues.forEach(
                (code, value) -> encoded.put(
                        Objects.requireNonNull(code, "reported 属性编码不能为空"),
                        json.encodeValue(value)));
        return desired.updateConvergedAtIfValuesMatch(
                deviceId,
                bindingGeneration,
                encoded,
                Objects.requireNonNull(now, "desired 收敛时间不能为空"))
                .stream()
                .map(row -> new DesiredConvergence(row.propertyCode(), row.messageId()))
                .toList();
    }

    /** 将下行消息查询结果转换成包含具体策略实例的领域消息。 */
    private DeviceDownlink toMessage(EncodedDownlinkMessage row) {
        Long desiredSequence = row.desiredSequence();
        return new DeviceDownlink(
                row.messageId(),
                row.deviceId(),
                row.bindingGeneration(),
                row.kind(),
                row.targetCode(),
                desiredSequence,
                row.scheduleId(),
                row.scheduledAt(),
                json.decodeMap(row.dataJson()),
                policies.acknowledgement(row.acknowledgementPolicy()),
                Duration.ofMillis(row.acknowledgementTimeoutMillis()),
                policies.retry(row.retryPolicy()),
                row.status(),
                row.attemptCount(),
                row.expiresAt(),
                row.createdAt());
    }

    /** 把回执查询结果转换成公开业务回执。 */
    private DeviceAcknowledgement toAcknowledgement(EncodedDeviceAcknowledgement row) {
        return new DeviceAcknowledgement(
                row.messageId(),
                row.ackCode(),
                json.decodeMap(row.dataJson()),
                row.source(),
                row.sourceAcknowledgementId(),
                row.acknowledgedAt(),
                row.receivedAt());
    }

}
