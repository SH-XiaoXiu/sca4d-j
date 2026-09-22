package com.sca4d.notification.sms;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 请求查询一次已经提交的 SMS 是否最终送达。 */
public final class SmsDeliveryCheck extends Message {
    public static final @NotNull TreeCode CODE = TreeCode.of("NOTIFICATION/SMS/DELIVERY_CHECK");

    public static final int PROTOCOL_VERSION = 1;

    private final @NotNull Notification notification;

    private final @NotNull MessageId messageId;

    private final long providerId;

    private final @Nullable String requestId;

    private final @Nullable String bizId;

    private final int attempt;

    /** 创建当前对象。 */
    public SmsDeliveryCheck(
            @NotNull Notification notification,
            @NotNull MessageId messageId,
            long providerId,
            @Nullable String requestId,
            @Nullable String bizId,
            int attempt) {
        if (providerId <= 0 || attempt <= 0) {
            throw new IllegalArgumentException("SMS Provider 标识和查询次数必须为正数");
        }
        this.notification = Objects.requireNonNull(notification, "通知不能为空");
        this.messageId = Objects.requireNonNull(messageId, "原始通知消息标识不能为空");
        this.providerId = providerId;
        this.requestId = requestId;
        this.bizId = bizId;
        this.attempt = attempt;
    }

    /** 执行 notification 方法表示的业务操作。 */
    public @NotNull Notification notification() {
        return notification;
    }

    /** 返回 messageId 方法提供的结果。 */
    public @NotNull MessageId messageId() {
        return messageId;
    }

    /** 执行 providerId 方法表示的业务操作。 */
    public long providerId() {
        return providerId;
    }

    /** 执行 requestId 方法表示的业务操作。 */
    public @Nullable String requestId() {
        return requestId;
    }

    /** 执行 bizId 方法表示的业务操作。 */
    public @Nullable String bizId() {
        return bizId;
    }

    /** 返回 attempt 方法提供的结果。 */
    public int attempt() {
        return attempt;
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }
}
