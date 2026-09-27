package com.sca4d.messaging.outbox;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** 保存当前派发批次已经租用的 Outbox 消息和条件更新依据。 */
final class OutboxClaim {

    private final @NotNull Id messageId;

    private final @NotNull TreeCode messageCode;

    private final byte @NotNull [] envelope;

    private final int attemptCount;

    private final @NotNull String leaseId;

    private final long version;

    private @NotNull OutboxStatus status = OutboxStatus.SENDING;

    private @Nullable Instant availableAt;

    private @Nullable Instant sentAt;

    private @Nullable String lastError;

    /** 创建已被当前派发批次改为 {@link OutboxStatus#SENDING} 的领取结果。 */
    OutboxClaim(
            @NotNull Id messageId,
            @NotNull TreeCode messageCode,
            byte @NotNull [] envelope,
            int attemptCount,
            @NotNull String leaseId,
            long version) {
        this.messageId = Objects.requireNonNull(messageId, "Id 不能为空");
        this.messageCode = Objects.requireNonNull(messageCode, "消息编码不能为空");
        this.envelope = Arrays.copyOf(Objects.requireNonNull(envelope, "消息信封不能为空"), envelope.length);
        this.attemptCount = attemptCount;
        this.leaseId = Objects.requireNonNull(leaseId, "Outbox 租约标识不能为空");
        this.version = version;
    }

    /** @return 消息标识 */
    @NotNull
    Id messageId() {
        return messageId;
    }

    /** @return RabbitMQ routing key 使用的消息编码 */
    @NotNull
    TreeCode messageCode() {
        return messageCode;
    }

    /** @return 已序列化业务消息信封的副本 */
    byte @NotNull [] envelope() {
        return Arrays.copyOf(envelope, envelope.length);
    }

    /** @return 包含本次领取的累计发布次数 */
    int attemptCount() {
        return attemptCount;
    }

    /** @return 当前派发批次的租约标识 */
    @NotNull
    String leaseId() {
        return leaseId;
    }

    /** @return 领取后的数据库版本 */
    long version() {
        return version;
    }

    /** 在 RabbitMQ 确认后记录本次租约的成功结果。 */
    OutboxClaim sent(@NotNull Instant at) {
        OutboxClaim result = new OutboxClaim(messageId, messageCode, envelope, attemptCount, leaseId, version);
        result.status = OutboxStatus.SENT;
        result.sentAt = Objects.requireNonNull(at, "Outbox 确认时间不能为空");
        return result;
    }

    /** 记录本次租约失败及业务层确定的下一次尝试时间。 */
    OutboxClaim failed(@NotNull Instant nextAttemptAt, @NotNull String error) {
        OutboxClaim result = new OutboxClaim(messageId, messageCode, envelope, attemptCount, leaseId, version);
        result.status = OutboxStatus.FAILED;
        result.availableAt = Objects.requireNonNull(nextAttemptAt, "Outbox 重试时间不能为空");
        result.lastError = Objects.requireNonNull(error, "Outbox 失败摘要不能为空");
        return result;
    }

    /** @return 本次租约的业务处理结果 */
    @NotNull
    OutboxStatus status() {
        return status;
    }

    /** @return 失败后确定的下一次尝试时间；成功时未修改 */
    @Nullable
    Instant availableAt() {
        return availableAt;
    }

    /** @return RabbitMQ 确认时间；未确认时为空 */
    @Nullable
    Instant sentAt() {
        return sentAt;
    }

    /** @return 本次失败摘要；成功时为空 */
    @Nullable
    String lastError() {
        return lastError;
    }
}
