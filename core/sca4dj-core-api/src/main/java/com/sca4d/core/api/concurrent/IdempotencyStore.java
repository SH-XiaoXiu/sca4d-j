package com.sca4d.core.api.concurrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.time.Duration;

/** 使用集群共享原子存储区分首次请求、处理中请求和可重放的成功结果。 */
public interface IdempotencyStore {

    /**
     * 原子尝试取得指定幂等请求的处理所有权。
     *
     * @param key 幂等键
     * @param fingerprint 当前方法和参数的请求指纹
     * @param resultType 方法声明的泛型返回类型
     * @param processingTtl 节点中断后允许其他节点重新执行的处理租约
     * @param retryAfter 同一请求仍在执行时建议调用方等待的时间
     * @param <T> 结果类型
     * @return 首次取得、成功重放、仍在处理或键冲突的互斥结果
     */
    @NotNull
    <T> IdempotencyBeginResult<T> begin(
            @NotNull IdempotencyKey key,
            @NotNull String fingerprint,
            @NotNull Type resultType,
            @NotNull Duration processingTtl,
            @NotNull Duration retryAfter);

    /**
     * 仅在 token 仍拥有处理中记录时原子保存成功结果。
     *
     * @param key {@link #begin} 使用的幂等键
     * @param token 首次取得结果签发的所有权 token
     * @param fingerprint 首次取得处理权时使用的请求指纹
     * @param result 业务结果，方法明确返回空值时为 {@code null}
     * @param resultType 方法声明的泛型返回类型
     * @param retention 保留时间
     * @return 当前 token 成功把记录推进为已完成时返回 {@code true}
     */
    <T> boolean succeed(
            @NotNull IdempotencyKey key,
            @NotNull String token,
            @NotNull String fingerprint,
            @Nullable T result,
            @NotNull Type resultType,
            @NotNull Duration retention);

    /**
     * 仅在 token 仍拥有处理中记录时原子释放失败执行的所有权。
     *
     * @param key {@link #begin} 使用的幂等键
     * @param token 首次取得结果签发的所有权 token
     * @return 当前 token 对应的处理中记录被删除时返回 {@code true}
     */
    boolean release(@NotNull IdempotencyKey key, @NotNull String token);
}
