package com.sca4d.iot.api.message;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** 保存 desired 或 command 共用的标准下行消息快照。 */
public final class DeviceDownlink {

    private final long messageId;

    private final long deviceId;

    private final long bindingGeneration;

    private final @NotNull DeviceDownlinkKind kind;

    private final @NotNull String targetCode;

    private final @Nullable Long desiredSequence;

    private final @Nullable Long scheduleId;

    private final @Nullable Instant scheduledAt;

    private final @NotNull Map<String, Object> data;

    private final @NotNull AcknowledgementPolicy acknowledgementPolicy;

    private final @NotNull Duration acknowledgementTimeout;

    private final @NotNull RetryPolicy retryPolicy;

    private final @NotNull DeviceDownlinkStatus status;

    private final int attemptCount;

    private final @Nullable Instant expiresAt;

    private final @NotNull Instant createdAt;

    /** 创建由 Repository 恢复或 Service 新建的标准下行消息。 */
    @SuppressWarnings("ParameterNumber")
    public DeviceDownlink(
            long messageId,
            long deviceId,
            long bindingGeneration,
            @NotNull DeviceDownlinkKind kind,
            @NotNull String targetCode,
            @Nullable Long desiredSequence,
            @Nullable Long scheduleId,
            @Nullable Instant scheduledAt,
            @NotNull Map<String, Object> data,
            @NotNull AcknowledgementPolicy acknowledgementPolicy,
            @NotNull Duration acknowledgementTimeout,
            @NotNull RetryPolicy retryPolicy,
            @NotNull DeviceDownlinkStatus status,
            int attemptCount,
            @Nullable Instant expiresAt,
            @NotNull Instant createdAt) {
        if (messageId <= 0 || deviceId <= 0 || bindingGeneration <= 0 || attemptCount < 0) {
            throw new IllegalArgumentException("下行消息标识、绑定代数 和尝试次数不合法");
        }
        if ((kind == DeviceDownlinkKind.DESIRED) == (desiredSequence == null)) {
            throw new IllegalArgumentException("只有 desired 下行消息必须包含 desired sequence");
        }
        if ((scheduleId == null) != (scheduledAt == null)
                || (scheduleId != null && (scheduleId <= 0 || kind != DeviceDownlinkKind.COMMAND))) {
            throw new IllegalArgumentException("只有定时 command 可以同时包含 scheduleId 和 scheduledAt");
        }
        this.messageId = messageId;
        this.deviceId = deviceId;
        this.bindingGeneration = bindingGeneration;
        this.kind = Objects.requireNonNull(kind, "下行消息类型不能为空");
        this.targetCode = TextUtils.requireText(targetCode, "下行目标 code");
        this.desiredSequence = desiredSequence;
        this.scheduleId = scheduleId;
        this.scheduledAt = scheduledAt;
        this.data = Map.copyOf(Objects.requireNonNull(data, "下行数据不能为空"));
        this.acknowledgementPolicy = Objects.requireNonNull(acknowledgementPolicy, "ACK 策略不能为空");
        this.acknowledgementTimeout = Objects.requireNonNull(acknowledgementTimeout, "ACK 等待时间不能为空");
        if (acknowledgementTimeout.isZero()
                || acknowledgementTimeout.isNegative()
                || acknowledgementTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("ACK 等待时间必须至少为 1 毫秒");
        }
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retry 策略不能为空");
        this.status = Objects.requireNonNull(status, "下行状态不能为空");
        this.attemptCount = attemptCount;
        this.expiresAt = expiresAt;
        this.createdAt = Objects.requireNonNull(createdAt, "下行创建时间不能为空");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("下行最晚发送时间必须晚于创建时间");
        }
    }

    /** @return 全局消息标识 */
    public long messageId() {
        return messageId;
    }

    /** @return 目标 Device */
    public long deviceId() {
        return deviceId;
    }

    /** @return 创建消息时的物模型绑定代数 */
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /** @return desired 或 command */
    public @NotNull DeviceDownlinkKind kind() {
        return kind;
    }

    /** @return 属性或命令 code */
    public @NotNull String targetCode() {
        return targetCode;
    }

    /** @return desired sequence；command 为空 */
    public @NotNull OptionalLong desiredSequence() {
        return desiredSequence == null ? OptionalLong.empty() : OptionalLong.of(desiredSequence);
    }

    /** @return 产生该 command 的设备定时规则标识；即时下行为空 */
    public @NotNull OptionalLong scheduleId() {
        return scheduleId == null ? OptionalLong.empty() : OptionalLong.of(scheduleId);
    }

    /** @return 设备定时规则本次原定触发时间；即时下行为空 */
    public @NotNull Optional<Instant> scheduledAt() {
        return Optional.ofNullable(scheduledAt);
    }

    /** @return 不可修改标准数据 */
    public @NotNull @Unmodifiable Map<String, Object> data() {
        return data;
    }

    /** @return ACK 策略 code */
    public @NotNull AcknowledgementPolicy acknowledgementPolicy() {
        return acknowledgementPolicy;
    }

    /** @return 创建消息时快照的业务 ACK 等待时间 */
    public @NotNull Duration acknowledgementTimeout() {
        return acknowledgementTimeout;
    }

    /** @return retry 策略 code */
    public @NotNull RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    /** @return 当前持久化状态 */
    public @NotNull DeviceDownlinkStatus status() {
        return status;
    }

    /** @return 已开始的发送尝试次数 */
    public int attemptCount() {
        return attemptCount;
    }

    /** @return 首次成功发布的最晚时间；没有限制时为空 */
    public @NotNull Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    /** @return 消息创建时间 */
    public @NotNull Instant createdAt() {
        return createdAt;
    }
}
