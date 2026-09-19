package com.sca4d.iot.persistence;

import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** 映射 desired 或 command 共用的下行消息持久化状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class EncodedDownlinkMessage {

    private final long messageId;

    private final long deviceId;

    private final long bindingGeneration;

    private final @NotNull DeviceDownlinkKind kind;

    private final @NotNull String targetCode;

    private final @Nullable Long desiredSequence;

    private final @Nullable Long scheduleId;

    private final @Nullable Instant scheduledAt;

    private final @NotNull String dataJson;

    private final @NotNull String acknowledgementPolicy;

    private final long acknowledgementTimeoutMillis;

    private final @NotNull String retryPolicy;

    private final @NotNull DeviceDownlinkStatus status;

    private final int attemptCount;

    private final @Nullable Instant expiresAt;

    private final @NotNull Instant createdAt;

    /** 创建 Mapper 返回的完整下行消息状态。 */
    @SuppressWarnings("ParameterNumber")
    public EncodedDownlinkMessage(
            long messageId,
            long deviceId,
            long bindingGeneration,
            @NotNull DeviceDownlinkKind kind,
            @NotNull String targetCode,
            @Nullable Long desiredSequence,
            @Nullable Long scheduleId,
            @Nullable Instant scheduledAt,
            @NotNull String dataJson,
            @NotNull String acknowledgementPolicy,
            long acknowledgementTimeoutMillis,
            @NotNull String retryPolicy,
            @NotNull DeviceDownlinkStatus status,
            int attemptCount,
            @Nullable Instant expiresAt,
            @NotNull Instant createdAt) {
        this.messageId = messageId;
        this.deviceId = deviceId;
        this.bindingGeneration = bindingGeneration;
        this.kind = kind;
        this.targetCode = targetCode;
        this.desiredSequence = desiredSequence;
        this.scheduleId = scheduleId;
        this.scheduledAt = scheduledAt;
        this.dataJson = dataJson;
        this.acknowledgementPolicy = acknowledgementPolicy;
        if (acknowledgementTimeoutMillis < 1) {
            throw new IllegalArgumentException("ACK 等待时间必须至少为 1 毫秒");
        }
        this.acknowledgementTimeoutMillis = acknowledgementTimeoutMillis;
        this.retryPolicy = retryPolicy;
        this.status = status;
        this.attemptCount = attemptCount;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    /** @return messageId */
    public long messageId() {
        return messageId;
    }

    /** @return Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return 绑定代数 */
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /** @return DESIRED 或 COMMAND */
    public @NotNull DeviceDownlinkKind kind() {
        return kind;
    }

    /** @return 属性或命令 code */
    public @NotNull String targetCode() {
        return targetCode;
    }

    /** @return desired sequence */
    public @Nullable Long desiredSequence() {
        return desiredSequence;
    }

    /** @return 可选设备定时规则标识 */
    public @Nullable Long scheduleId() {
        return scheduleId;
    }

    /** @return 可选设备定时原计划时间 */
    public @Nullable Instant scheduledAt() {
        return scheduledAt;
    }

    /** @return 标准数据 JSON */
    public @NotNull String dataJson() {
        return dataJson;
    }

    /** @return ACK 策略 code */
    public @NotNull String acknowledgementPolicy() {
        return acknowledgementPolicy;
    }

    /** @return 创建消息时快照的 ACK 等待时间，单位为毫秒 */
    public long acknowledgementTimeoutMillis() {
        return acknowledgementTimeoutMillis;
    }

    /** @return retry 策略 code */
    public @NotNull String retryPolicy() {
        return retryPolicy;
    }

    /** @return 当前状态 */
    public @NotNull DeviceDownlinkStatus status() {
        return status;
    }

    /** @return 已开始尝试次数 */
    public int attemptCount() {
        return attemptCount;
    }

    /** @return 最晚发送时间 */
    public @Nullable Instant expiresAt() {
        return expiresAt;
    }

    /** @return 创建时间 */
    public @NotNull Instant createdAt() {
        return createdAt;
    }
}
