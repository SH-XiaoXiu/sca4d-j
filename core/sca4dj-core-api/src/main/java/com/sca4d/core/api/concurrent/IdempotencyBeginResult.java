package com.sca4d.core.api.concurrent;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/** 保存一次幂等处理权竞争的状态以及对应状态允许读取的数据。 */
public class IdempotencyBeginResult<T> {

    private final @NotNull IdempotencyBeginStatus status;

    private final @Nullable String token;

    private final @Nullable T replayResult;

    private final @NotNull Duration retryAfter;

    private IdempotencyBeginResult(
            @NotNull IdempotencyBeginStatus status,
            @Nullable String token,
            @Nullable T replayResult,
            @NotNull Duration retryAfter) {
        this.status = Objects.requireNonNull(status, "幂等开始状态不能为空");
        this.token = token;
        this.replayResult = replayResult;
        this.retryAfter = Objects.requireNonNull(retryAfter, "建议重试时间不能为空");
    }

    /** @param token 当前执行唯一的所有权 token @return 已取得处理权的结果 */
    @Contract("_ -> new")
    public static <T> @NotNull IdempotencyBeginResult<T> acquired(@NotNull String token) {
        return new IdempotencyBeginResult<>(
                IdempotencyBeginStatus.ACQUIRED,
                Objects.requireNonNull(token, "所有权 token 不能为空"),
                null,
                Duration.ZERO);
    }

    /** @param result 已保存的业务结果，可以是明确保存的空值 @return 可直接重放的结果 */
    @Contract("_ -> new")
    public static <T> @NotNull IdempotencyBeginResult<T> replay(@Nullable T result) {
        return new IdempotencyBeginResult<>(IdempotencyBeginStatus.REPLAY, null, result, Duration.ZERO);
    }

    /** @param retryAfter 建议再次提交前等待的正数时间 @return 仍在处理的结果 */
    @Contract("_ -> new")
    public static <T> @NotNull IdempotencyBeginResult<T> inProgress(@NotNull Duration retryAfter) {
        Duration checked = Objects.requireNonNull(retryAfter, "建议重试时间不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException("建议重试时间必须为正数");
        }
        return new IdempotencyBeginResult<>(IdempotencyBeginStatus.IN_PROGRESS, null, null, checked);
    }

    /** @return 幂等键已经绑定不同请求的冲突结果 */
    @Contract("-> new")
    public static <T> @NotNull IdempotencyBeginResult<T> conflict() {
        return new IdempotencyBeginResult<>(IdempotencyBeginStatus.CONFLICT, null, null, Duration.ZERO);
    }

    /** @return 当前幂等开始状态 */
    @Contract(pure = true)
    public @NotNull IdempotencyBeginStatus status() {
        return status;
    }

    /** @return 仅 {@link IdempotencyBeginStatus#ACQUIRED} 状态拥有的 token */
    @Contract(pure = true)
    public @Nullable String token() {
        return token;
    }

    /**
     * 返回成功重放的结果。
     *
     * @return 仅 {@link IdempotencyBeginStatus#REPLAY} 状态可读取；首次结果可以明确为 {@code null}
     */
    @Contract(pure = true)
    public @Nullable T replayResult() {
        return replayResult;
    }

    /** @return 仅 {@link IdempotencyBeginStatus#IN_PROGRESS} 状态返回正数时间；其他状态返回零 */
    @Contract(pure = true)
    public @NotNull Duration retryAfter() {
        return retryAfter;
    }
}
