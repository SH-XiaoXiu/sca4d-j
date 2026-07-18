package com.sca4d.common.concurrent.idempotency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** Redis 原子开始处理后返回的互斥状态结果。 */
public class IdempotencyBeginResult {

    private final IdempotencyBeginStatus status;
    private final String token;
    private final IdempotencyRecord record;
    private final Duration retryAfter;

    /**
     * 创建只允许对应状态字段有值的开始处理结果。
     *
     * @param status ACQUIRED、REPLAY、IN_PROGRESS 或 CONFLICT
     * @param token 仅 ACQUIRED 状态拥有的所有权 token
     * @param record 仅 REPLAY 状态拥有的历史成功记录
     * @param retryAfter 仅 IN_PROGRESS 状态使用的建议等待时间
     */
    private IdempotencyBeginResult(
            @NotNull IdempotencyBeginStatus status,
            @Nullable String token,
            @Nullable IdempotencyRecord record,
            @Nullable Duration retryAfter) {
        this.status = status;
        this.token = token;
        this.record = record;
        this.retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
    }

    /**
     * @param token 当前首次执行的唯一所有权 token
     * @return ACQUIRED 结果
     */
    public static @NotNull IdempotencyBeginResult acquired(@NotNull String token) {
        return new IdempotencyBeginResult(IdempotencyBeginStatus.ACQUIRED, token, null, null);
    }

    /**
     * @param record 与当前请求指纹相同的历史成功记录
     * @return REPLAY 结果
     */
    public static @NotNull IdempotencyBeginResult replay(@NotNull IdempotencyRecord record) {
        return new IdempotencyBeginResult(IdempotencyBeginStatus.REPLAY, null, record, null);
    }

    /**
     * @param retryAfter 建议客户端再次查询结果前等待的时间
     * @return IN_PROGRESS 结果
     */
    public static @NotNull IdempotencyBeginResult inProgress(@NotNull Duration retryAfter) {
        return new IdempotencyBeginResult(IdempotencyBeginStatus.IN_PROGRESS, null, null, retryAfter);
    }

    /** @return 同一 key 已被不同请求指纹使用的 CONFLICT 结果 */
    public static @NotNull IdempotencyBeginResult conflict() {
        return new IdempotencyBeginResult(IdempotencyBeginStatus.CONFLICT, null, null, null);
    }

    /** @return 当前互斥状态 */
    public @NotNull IdempotencyBeginStatus getStatus() {
        return status;
    }

    /** @return ACQUIRED 状态的所有权 token，其他状态为 null */
    public @Nullable String getToken() {
        return token;
    }

    /** @return REPLAY 状态的历史成功记录，其他状态为 null */
    public @Nullable IdempotencyRecord getRecord() {
        return record;
    }

    /** @return IN_PROGRESS 状态的建议等待时间，其他状态为 Duration.ZERO */
    public @NotNull Duration getRetryAfter() {
        return retryAfter;
    }
}
