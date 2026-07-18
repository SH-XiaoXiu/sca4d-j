package com.sca4d.common.concurrent.idempotency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Redis 中保存的处理中所有权或可重放成功响应。 */
public class IdempotencyRecord {

    private IdempotencyStatus status;
    private String fingerprint;
    private String token;
    private String responseJson;
    private String responseType;
    private long createdAt;

    /** 创建供 Jackson 反序列化逐字段填充的空记录。 */
    public IdempotencyRecord() {
    }

    /**
     * 创建带有限租约的处理中所有权记录。
     *
     * @param fingerprint 首次执行请求指纹
     * @param token 当前节点本次执行的唯一所有权 token
     * @return PROCESSING 记录
     */
    public static @NotNull IdempotencyRecord processing(@NotNull String fingerprint, @NotNull String token) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setStatus(IdempotencyStatus.PROCESSING);
        record.setFingerprint(fingerprint);
        record.setToken(token);
        record.setCreatedAt(System.currentTimeMillis());
        return record;
    }

    /**
     * 创建可在成功保留窗口内重放的响应记录。
     *
     * @param fingerprint 首次执行请求指纹
     * @param responseJson 首次执行响应 JSON，null 返回值时为 null
     * @param responseType 首次执行方法声明的泛型返回类型
     * @return 不再携带所有权 token 的 SUCCEEDED 记录
     */
    public static @NotNull IdempotencyRecord succeeded(
            @NotNull String fingerprint,
            @Nullable String responseJson,
            @NotNull String responseType) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setStatus(IdempotencyStatus.SUCCEEDED);
        record.setFingerprint(fingerprint);
        record.setResponseJson(responseJson);
        record.setResponseType(responseType);
        record.setCreatedAt(System.currentTimeMillis());
        return record;
    }

    /** @return PROCESSING 或 SUCCEEDED，反序列化损坏记录时可能为 null */
    public @Nullable IdempotencyStatus getStatus() {
        return status;
    }

    /** @param status Redis 记录当前是处理中、已成功还是可重试 */
    public void setStatus(@Nullable IdempotencyStatus status) {
        this.status = status;
    }

    /** @return 用于同 key 请求冲突判断的指纹，损坏记录时可能为 null */
    public @Nullable String getFingerprint() {
        return fingerprint;
    }

    /** @param fingerprint 首次执行请求指纹 */
    public void setFingerprint(@Nullable String fingerprint) {
        this.fingerprint = fingerprint;
    }

    /** @return PROCESSING 所有权 token，SUCCEEDED 记录为 null */
    public @Nullable String getToken() {
        return token;
    }

    /** @param token PROCESSING 所有权 token */
    public void setToken(@Nullable String token) {
        this.token = token;
    }

    /** @return SUCCEEDED 响应 JSON，null 返回值或 PROCESSING 记录为 null */
    public @Nullable String getResponseJson() {
        return responseJson;
    }

    /** @param responseJson 首次成功响应 JSON */
    public void setResponseJson(@Nullable String responseJson) {
        this.responseJson = responseJson;
    }

    /** @return SUCCEEDED 方法返回类型，PROCESSING 记录为 null */
    public @Nullable String getResponseType() {
        return responseType;
    }

    /** @param responseType 首次执行方法声明的泛型返回类型 */
    public void setResponseType(@Nullable String responseType) {
        this.responseType = responseType;
    }

    /** @return 创建该 Redis 状态记录的节点时间戳，仅用于诊断，不参与租约判断 */
    public long getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt 创建该状态记录的节点 Epoch 毫秒时间 */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
