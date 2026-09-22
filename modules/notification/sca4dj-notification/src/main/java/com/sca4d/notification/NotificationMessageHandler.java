package com.sca4d.notification;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.delivery.DeliveryOutcome;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.HandlingPolicy;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationDelivery;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 让一个 NotificationDelivery 独立消费 NotificationMessage 并拥有自己的队列和 DLQ。 */
public final class NotificationMessageHandler implements MessageHandler<NotificationMessage> {

    private final @NotNull NotificationChannel channel;

    private final @NotNull HandlingPolicy handlingPolicy;

    private final @NotNull NotificationDeliveryRouter deliveries;

    private final @NotNull NotificationMessageCodec codec;

    /** 创建当前对象。 */
    public NotificationMessageHandler(
            @NotNull NotificationDelivery delivery,
            @NotNull NotificationDeliveryRouter deliveries,
            @NotNull NotificationMessageCodec codec) {
        this.channel = Objects.requireNonNull(delivery, "通知投递实现不能为空").channel();
        this.handlingPolicy = delivery.handlingPolicy();
        this.deliveries = Objects.requireNonNull(deliveries, "通知渠道路由器不能为空");
        this.codec = Objects.requireNonNull(codec, "通知消息 Codec 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of(channel.value() + "/DELIVERY");
    }

    @Override
    public @NotNull MessageCodec<NotificationMessage> codec() {
        return codec;
    }

    @Override
    public @NotNull HandlingPolicy handlingPolicy() {
        return handlingPolicy;
    }

    @Override
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<NotificationMessage> incoming) {
        IncomingMessage<NotificationMessage> checked = Objects.requireNonNull(incoming, "通知消息不能为空");
        if (!checked.message().notification().channels().contains(channel)) {
            return HandlingDecision.acknowledge();
        }
        DeliveryOutcome outcome = deliveries.deliver(
                channel,
                checked.messageId(),
                checked.message().notification());
        String failureCode = outcome.failureCode();
        return switch (outcome.status()) {
            case CONFIRMED, PENDING -> HandlingDecision.acknowledge();
            case UNKNOWN -> HandlingDecision.retry(
                    failureCode == null ? "NOTIFICATION_RESULT_UNKNOWN" : failureCode);
            case RETRYABLE_FAILURE -> HandlingDecision.retry(
                    failureCode == null ? "NOTIFICATION_RETRYABLE_FAILURE" : failureCode);
            case REJECTED -> HandlingDecision.reject(
                    failureCode == null ? "NOTIFICATION_REJECTED" : failureCode);
        };
    }
}
