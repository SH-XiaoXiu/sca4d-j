package com.sca4d.notification.sms;

import com.sca4d.core.api.delivery.DeliveryOutcome;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 消费延迟 SMS 查询消息，并把最终送达状态交给 SMS 渠道实现。 */
public final class SmsDeliveryCheckHandler implements MessageHandler<SmsDeliveryCheck> {
    private final @NotNull SmsNotificationDelivery delivery;

    private final @NotNull SmsDeliveryCheckCodec codec;

    public SmsDeliveryCheckHandler(
            @NotNull SmsNotificationDelivery delivery,
            @NotNull SmsDeliveryCheckCodec codec) {
        this.delivery = Objects.requireNonNull(delivery, "SMS 投递实现不能为空");
        this.codec = Objects.requireNonNull(codec, "SMS 查询 Codec 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("NOTIFICATION/SMS/DELIVERY_CHECK/HANDLER");
    }

    @Override
    public @NotNull MessageCodec<SmsDeliveryCheck> codec() {
        return codec;
    }

    @Override
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<SmsDeliveryCheck> incoming) {
        IncomingMessage<SmsDeliveryCheck> checked = Objects.requireNonNull(incoming, "SMS 查询消息不能为空");
        DeliveryOutcome outcome = delivery.check(checked.message());
        String failureCode = outcome.failureCode();
        return switch (outcome.status()) {
            case CONFIRMED, PENDING -> HandlingDecision.acknowledge();
            case REJECTED, RETRYABLE_FAILURE, UNKNOWN -> HandlingDecision.reject(
                    failureCode == null ? "SMS_DELIVERY_CHECK_FAILED" : failureCode);
        };
    }
}
