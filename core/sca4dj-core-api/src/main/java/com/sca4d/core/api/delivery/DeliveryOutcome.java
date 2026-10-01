package com.sca4d.core.api.delivery;

import static com.sca4d.core.api.delivery.DeliveryOutcome.Status.CONFIRMED;
import static com.sca4d.core.api.delivery.DeliveryOutcome.Status.PENDING;
import static com.sca4d.core.api.delivery.DeliveryOutcome.Status.REJECTED;
import static com.sca4d.core.api.delivery.DeliveryOutcome.Status.RETRYABLE_FAILURE;
import static com.sca4d.core.api.delivery.DeliveryOutcome.Status.UNKNOWN;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存一次业务投递已经确认、等待查询、可以重试、拒绝或结果未知的状态。 */
public class DeliveryOutcome {

    public enum Status {
        CONFIRMED,
        PENDING,
        RETRYABLE_FAILURE,
        REJECTED,
        UNKNOWN
    }

    private final @Nullable Instant nextActionAt;

    private final @NotNull Status status;

    private final @Nullable String externalRequestId;

    private final @Nullable String failureCode;

    /** 创建业务投递结果。 */
    public DeliveryOutcome(
            @NotNull Status status,
            @Nullable Instant nextActionAt,
            @Nullable String externalRequestId,
            @Nullable String failureCode) {
        this.status = Objects.requireNonNull(status, "投递结果不能为空");
        this.nextActionAt = nextActionAt;
        this.externalRequestId = text(externalRequestId);
        this.failureCode = text(failureCode);
    }

    /** @return 已确认的投递结果 */
    @CheckReturnValue
    public static @NotNull DeliveryOutcome confirmed() {
        return new DeliveryOutcome(CONFIRMED, null, null, null);
    }

    /** @param nextActionAt 下一次动作时间 @param externalRequestId 外部请求标识 @return 待查询结果 */
    @CheckReturnValue
    public static @NotNull DeliveryOutcome pending(
            @NotNull Instant nextActionAt,
            @Nullable String externalRequestId) {
        return new DeliveryOutcome(PENDING, nextActionAt, externalRequestId, null);
    }

    /** @param nextActionAt 下一次重试时间 @param failureCode 稳定失败编码 @return 可重试失败结果 */
    @CheckReturnValue
    public static @NotNull DeliveryOutcome retryableFailure(
            @Nullable Instant nextActionAt,
            @NotNull String failureCode) {
        return new DeliveryOutcome(RETRYABLE_FAILURE, nextActionAt, null, failureCode);
    }

    /** @param failureCode 稳定失败编码 @return 不可重试拒绝结果 */
    @CheckReturnValue
    public static @NotNull DeliveryOutcome rejected(@NotNull String failureCode) {
        return new DeliveryOutcome(REJECTED, null, null, failureCode);
    }

    /** @param nextActionAt 下一次动作时间 @param externalRequestId 外部请求标识 @param failureCode 失败编码 @return 未知结果 */
    @CheckReturnValue
    public static @NotNull DeliveryOutcome unknown(
            @NotNull Instant nextActionAt,
            @Nullable String externalRequestId,
            @Nullable String failureCode) {
        return new DeliveryOutcome(UNKNOWN, nextActionAt, externalRequestId, failureCode);
    }

    /** @return 投递状态 */
    public @NotNull Status status() {
        return status;
    }

    /** @return 下一次动作时间 */
    public @Nullable Instant nextActionAt() {
        return nextActionAt;
    }

    /** @return 外部请求标识 */
    public @Nullable String externalRequestId() {
        return externalRequestId;
    }

    /** @return 稳定失败编码 */
    public @Nullable String failureCode() {
        return failureCode;
    }

    private static @Nullable String text(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
