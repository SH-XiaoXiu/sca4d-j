package com.sca4d.core.api.delivery;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 保存一次业务投递已经确认、等待查询、可以重试、拒绝或结果未知的状态。 */
public final class DeliveryOutcome {

    public enum Status {
        CONFIRMED,
        PENDING,
        RETRYABLE_FAILURE,
        REJECTED,
        UNKNOWN
    }

    private final @NotNull Status status;

    private final @Nullable Instant nextActionAt;

    private final @Nullable String externalRequestId;

    private final @Nullable String failureCode;

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

    @CheckReturnValue
    public static @NotNull DeliveryOutcome confirmed() {
        return new DeliveryOutcome(Status.CONFIRMED, null, null, null);
    }

    @CheckReturnValue
    public static @NotNull DeliveryOutcome pending(
            @NotNull Instant nextActionAt,
            @Nullable String externalRequestId) {
        return new DeliveryOutcome(Status.PENDING, nextActionAt, externalRequestId, null);
    }

    @CheckReturnValue
    public static @NotNull DeliveryOutcome retryableFailure(
            @Nullable Instant nextActionAt,
            @NotNull String failureCode) {
        return new DeliveryOutcome(Status.RETRYABLE_FAILURE, nextActionAt, null, failureCode);
    }

    @CheckReturnValue
    public static @NotNull DeliveryOutcome rejected(@NotNull String failureCode) {
        return new DeliveryOutcome(Status.REJECTED, null, null, failureCode);
    }

    @CheckReturnValue
    public static @NotNull DeliveryOutcome unknown(
            @NotNull Instant nextActionAt,
            @Nullable String externalRequestId,
            @Nullable String failureCode) {
        return new DeliveryOutcome(Status.UNKNOWN, nextActionAt, externalRequestId, failureCode);
    }

    public @NotNull Status status() {
        return status;
    }

    public @Nullable Instant nextActionAt() {
        return nextActionAt;
    }

    public @Nullable String externalRequestId() {
        return externalRequestId;
    }

    public @Nullable String failureCode() {
        return failureCode;
    }

    private static @Nullable String text(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
