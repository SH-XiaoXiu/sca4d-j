package com.sca4d.common.concurrent.idempotency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 使用集群共享原子存储区分首次请求、处理中冲突和可直接重放的成功响应。 */
public interface IdempotencyStore {

    /**
     * 原子尝试获得指定业务操作与客户端意图的处理所有权。
     *
     * @param key 已包含应用前缀、固定操作摘要和客户端 key 摘要的存储 key
     * @param fingerprint 用于区分同 key 不同业务输入的请求指纹
     * @param processingTtl 节点中断后允许其他节点重新获得所有权的处理租约
     * @param retryAfter 同一请求仍在执行时返回客户端的建议等待时间
     * @return ACQUIRED 携带唯一所有权 token；其他状态不携带 token
     */
    @NotNull
    IdempotencyBeginResult begin(
            @NotNull String key,
            @NotNull String fingerprint,
            @NotNull Duration processingTtl,
            @NotNull Duration retryAfter);

    /**
     * 仅在 token 仍拥有处理中记录时原子保存成功响应。
     *
     * @param key {@link #begin(String, String, Duration, Duration)} 使用的存储 key
     * @param token ACQUIRED 结果签发给当前执行的所有权 token
     * @param fingerprint 首次执行使用的请求指纹
     * @param responseJson 首次成功响应 JSON，void 或 null 返回值时为 null
     * @param responseType 首次执行方法声明的泛型返回类型
     * @param successTtl 成功响应允许重放的保留时间
     * @return 当前 token 仍拥有处理中记录并成功推进为 SUCCEEDED 时返回 true
     */
    boolean succeed(
            @NotNull String key,
            @NotNull String token,
            @NotNull String fingerprint,
            @Nullable String responseJson,
            @NotNull String responseType,
            @NotNull Duration successTtl);

    /**
     * 仅在 token 仍拥有处理中记录时原子释放失败执行的所有权。
     *
     * @param key {@link #begin(String, String, Duration, Duration)} 使用的存储 key
     * @param token ACQUIRED 结果签发给当前执行的所有权 token
     * @return 当前 token 仍拥有处理中记录并成功删除时返回 true
     */
    boolean release(@NotNull String key, @NotNull String token);
}
