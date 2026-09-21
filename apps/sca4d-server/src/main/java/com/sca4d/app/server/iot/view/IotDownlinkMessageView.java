package com.sca4d.app.server.iot.view;

import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 返回 desired 或 command 生成的标准下行消息状态。 */
@SuppressWarnings("unused")
public final class IotDownlinkMessageView {

    private final long messageId;

    private final long deviceId;

    private final long bindingGeneration;

    private final @NotNull DeviceDownlinkKind kind;

    private final @NotNull String targetCode;

    private final @Nullable Long desiredSequence;

    private final @NotNull @Unmodifiable Map<String, Object> data;

    private final @NotNull String acknowledgementPolicy;

    private final @NotNull Duration acknowledgementTimeout;

    private final @NotNull String retryPolicy;

    private final @NotNull DeviceDownlinkStatus status;

    private final int attemptCount;

    private final @Nullable Instant expiresAt;

    private final @NotNull Instant createdAt;

    /** 从公共标准下行消息创建 HTTP 视图。 */
    private IotDownlinkMessageView(@NotNull DeviceDownlink message) {
        this.messageId = message.messageId();
        this.deviceId = message.deviceId();
        this.bindingGeneration = message.bindingGeneration();
        this.kind = message.kind();
        this.targetCode = message.targetCode();
        this.desiredSequence = message.desiredSequence().isPresent()
                ? message.desiredSequence().getAsLong()
                : null;
        this.data = message.data();
        this.acknowledgementPolicy = message.acknowledgementPolicy().code().value();
        this.acknowledgementTimeout = message.acknowledgementTimeout();
        this.retryPolicy = message.retryPolicy().code().value();
        this.status = message.status();
        this.attemptCount = message.attemptCount();
        this.expiresAt = message.expiresAt().orElse(null);
        this.createdAt = message.createdAt();
    }

    /** @return 标准下行消息 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotDownlinkMessageView from(@NotNull DeviceDownlink message) {
        return new IotDownlinkMessageView(Objects.requireNonNull(message, "下行消息不能为空"));
    }

    /** @return 全局消息标识 */
    public long getMessageId() {
        return messageId;
    }

    /** @return 目标 Device 标识 */
    public long getDeviceId() {
        return deviceId;
    }

    /** @return 创建消息时的物模型绑定代数 */
    public long getBindingGeneration() {
        return bindingGeneration;
    }

    /** @return desired 或 command */
    public @NotNull DeviceDownlinkKind getKind() {
        return kind;
    }

    /** @return 属性或命令 code */
    public @NotNull String getTargetCode() {
        return targetCode;
    }

    /** @return desired sequence；command 返回 null */
    public @Nullable Long getDesiredSequence() {
        return desiredSequence;
    }

    /** @return 标准消息数据 */
    public @NotNull @Unmodifiable Map<String, Object> getData() {
        return Map.copyOf(data);
    }

    /** @return ACK 策略 code */
    public @NotNull String getAcknowledgementPolicy() {
        return acknowledgementPolicy;
    }

    /** @return 创建消息时快照的 ACK 等待时间 */
    public @NotNull Duration getAcknowledgementTimeout() {
        return acknowledgementTimeout;
    }

    /** @return retry 策略 code */
    public @NotNull String getRetryPolicy() {
        return retryPolicy;
    }

    /** @return 当前持久化状态 */
    public @NotNull DeviceDownlinkStatus getStatus() {
        return status;
    }

    /** @return 已开始的发送尝试次数 */
    public int getAttemptCount() {
        return attemptCount;
    }

    /** @return 首次成功发布的最晚时间；即时消息为空 */
    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    /** @return 消息创建时间 */
    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }
}
