package com.sca4d.notification.sms;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存一次 SMS Provider 尝试的状态和查询所需的外部标识。 */
public final class SmsDeliveryAttempt {
    private final @NotNull SmsDeliveryStatus status;

    private final @Nullable String requestId;

    private final @Nullable String bizId;

    public SmsDeliveryAttempt(
            @NotNull SmsDeliveryStatus status,
            @Nullable String requestId,
            @Nullable String bizId) {
        this.status = Objects.requireNonNull(status, "SMS 投递状态不能为空");
        this.requestId = requestId;
        this.bizId = bizId;
    }

    /** 返回 status 方法提供的结果。 */
    public @NotNull SmsDeliveryStatus status() {
        return status;
    }

    public @Nullable String requestId() {
        return requestId;
    }

    public @Nullable String bizId() {
        return bizId;
    }
}
