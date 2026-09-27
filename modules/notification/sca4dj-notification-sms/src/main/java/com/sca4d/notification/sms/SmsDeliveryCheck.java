package com.sca4d.notification.sms;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 请求查询一次已经提交的 SMS 是否最终送达。 */
public class SmsDeliveryCheck extends Message {
    public static final @NotNull TreeCode CODE = TreeCode.of("NOTIFICATION/SMS/DELIVERY_CHECK");

    public static final int PROTOCOL_VERSION = 1;

    private final @NotNull Notification notification;

    private final @NotNull Id messageId;

    private final long providerId;

    private final @Nullable String requestId;

    private final @Nullable String bizId;

    private final int attempt;

    /**
     * @param notification 已冻结的通知
     * @param messageId 原始通知消息标识
     * @param providerId 待查询的 Provider 标识
     * @param requestId Provider 请求标识，未知时为空
     * @param bizId Provider 业务标识，未知时为空
     * @param attempt 当前查询次数，从 1 开始
     */
    public SmsDeliveryCheck(
            @NotNull Notification notification,
            @NotNull Id messageId,
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

    /** @return 已冻结的通知 */
    public @NotNull Notification notification() {
        return notification;
    }

    /** @return 原始通知消息标识 */
    public @NotNull Id messageId() {
        return messageId;
    }

    /** @return 待查询的 Provider 标识 */
    public long providerId() {
        return providerId;
    }

    /** @return Provider 请求标识，未知时为空 */
    public @Nullable String requestId() {
        return requestId;
    }

    /** @return Provider 业务标识，未知时为空 */
    public @Nullable String bizId() {
        return bizId;
    }

    /** @return 当前查询次数，从 1 开始 */
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
