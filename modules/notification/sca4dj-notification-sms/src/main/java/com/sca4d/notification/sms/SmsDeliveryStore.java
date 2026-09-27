package com.sca4d.notification.sms;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.notification.sms.mapper.SmsDeliveryMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 使用独立短事务登记和完成 SMS Provider 调用，不让外部请求占用数据库事务。 */
public class SmsDeliveryStore {
    private final SmsDeliveryMapper mapper;

    private final IdGenerator ids;

    /** 创建 SMS 投递状态存储。 */
    public SmsDeliveryStore(@NotNull SmsDeliveryMapper mapper, @NotNull IdGenerator ids) {
        this.mapper = Objects.requireNonNull(mapper, "SmsDeliveryMapper 不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
    }

    /** @return 已存在的 Provider 尝试和外部标识；尚未尝试时为空 */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public @Nullable SmsDeliveryAttempt attempt(@NotNull Id messageId, long providerId) {
        return mapper.selectAttempt(messageId, providerId);
    }

    /** @return 首次登记本次 Provider 尝试时为 true */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean begin(
            @NotNull Id messageId,
            @NotNull SmsProvider provider,
            @NotNull SmsRoute route,
            @NotNull String recipientHash,
            @NotNull String recipientMasked) {
        return mapper.insertAttempt(
                ids.nextId().value(),
                messageId,
                provider.providerId(),
                route.value(),
                recipientHash,
                recipientMasked) == 1;
    }

    /** 把 PENDING 尝试写成 Provider API 的最终确定程度。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(
            @NotNull Id messageId,
            @NotNull SmsProvider provider,
            @NotNull SmsProviderResult result) {
        SmsProviderResult checked = Objects.requireNonNull(result, "SMS Transport 结果不能为空");
        if (mapper.finish(
                messageId,
                provider.providerId(),
                checked.status(),
                checked.requestId(),
                checked.bizId(),
                checked.failureCode()) != 1) {
            throw new IllegalStateException("SMS Provider 尝试状态已经变化");
        }
    }
}
