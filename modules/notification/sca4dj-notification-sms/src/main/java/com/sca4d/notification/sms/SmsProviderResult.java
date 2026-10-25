package com.sca4d.notification.sms;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存 Provider API 接受、拒绝或结果未知及其非敏感标识。 */
public class SmsProviderResult {
    private final @NotNull SmsDeliveryStatus status;

    private final @Nullable String bizId;

    private final @Nullable String failureCode;

    private final @Nullable String requestId;

    /**
     * 保存一次 Provider 提交的结果；status 为空时抛出 NullPointerException，其余字段原样保存。
     *
     * @param status Provider API 对本次提交的确定程度
     * @param requestId Provider 返回的请求标识，没有返回时为空
     * @param bizId Provider 返回的业务标识，没有返回时为空
     * @param failureCode Provider 返回的失败码，成功时为空
     */
    public SmsProviderResult(
            @NotNull SmsDeliveryStatus status,
            @Nullable String requestId,
            @Nullable String bizId,
            @Nullable String failureCode) {
        this.status = Objects.requireNonNull(status, "SMS 投递状态不能为空");
        this.requestId = requestId;
        this.bizId = bizId;
        this.failureCode = failureCode;
    }

    /** @return Provider API 对本次提交的确定程度 */
    public @NotNull SmsDeliveryStatus status() {
        return status;
    }

    /** @return Provider 请求标识；没有返回时为空 */
    public @Nullable String requestId() {
        return requestId;
    }

    /** @return Provider 业务标识；没有返回时为空 */
    public @Nullable String bizId() {
        return bizId;
    }

    /** @return Provider 拒绝码或本地异常类型；成功时为空 */
    public @Nullable String failureCode() {
        return failureCode;
    }
}
