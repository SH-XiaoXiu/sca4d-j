package com.sca4d.notification.sms.mapper;

import com.sca4d.core.api.id.Id;
import com.sca4d.notification.sms.SmsDeliveryAttempt;
import com.sca4d.notification.sms.SmsDeliveryStatus;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 记录每个原始通知消息在各 Provider 的提交结果。 */
public interface SmsDeliveryMapper {
    /** @return 已存在状态；尚未尝试时为空 */
    @Nullable
    SmsDeliveryAttempt selectAttempt(
            @Param("messageId") @NotNull Id messageId,
            @Param("providerId") long providerId);

    /** @return 首次登记 Provider 尝试为 1，已有记录为 0 */
    int insertAttempt(
            @Param("deliveryId") long deliveryId,
            @Param("messageId") @NotNull Id messageId,
            @Param("providerId") long providerId,
            @Param("route") @NotNull String route,
            @Param("recipientHash") @NotNull String recipientHash,
            @Param("recipientMasked") @NotNull String recipientMasked);

    /** @return 从 PENDING 写入最终 API 结果的行数 */
    int finish(
            @Param("messageId") @NotNull Id messageId,
            @Param("providerId") long providerId,
            @Param("status") @NotNull SmsDeliveryStatus status,
            @Param("requestId") @Nullable String requestId,
            @Param("bizId") @Nullable String bizId,
            @Param("failureCode") @Nullable String failureCode);
}
