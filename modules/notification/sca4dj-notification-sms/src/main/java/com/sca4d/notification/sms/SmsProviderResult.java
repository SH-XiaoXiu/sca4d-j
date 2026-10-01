package com.sca4d.notification.sms;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存 Provider API 接受、拒绝或结果未知及其非敏感标识。 */
public class SmsProviderResult {
    private final @NotNull SmsDeliveryStatus status;

    private final @Nullable String requestId;

    private final @Nullable String bizId;

    private final @Nullable String failureCode;

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
