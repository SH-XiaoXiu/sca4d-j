package com.sca4d.notification;

import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationDelivery;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 为一个渠道消费 `notification.requested@2`，使各渠道分别确认或进入自己的 DLQ。 */
public final class NotificationRequestedConsumer implements MessageConsumer<NotificationRequested> {

    private final @NotNull NotificationChannel channel;

    private final @NotNull NotificationDeliveryRouter deliveries;

    /** @param delivery 当前 Consumer 独占的渠道投递实现 @param deliveries 通知渠道路由器 */
    public NotificationRequestedConsumer(
            @NotNull NotificationDelivery delivery,
            @NotNull NotificationDeliveryRouter deliveries) {
        this.channel = Objects.requireNonNull(delivery, "通知投递实现不能为空").channel();
        this.deliveries = Objects.requireNonNull(deliveries, "通知投递路由器不能为空");
    }

    @Override
    public @NotNull String consumerCode() {
        return "notification.delivery." + channel.value();
    }

    @Override
    public @NotNull EventType<NotificationRequested> eventType() {
        return NotificationEvents.REQUESTED;
    }

    @Override
    public void consume(@NotNull MessageDelivery<NotificationRequested> delivery) {
        MessageDelivery<NotificationRequested> checked = Objects.requireNonNull(delivery, "消息投递不能为空");
        deliveries.deliver(channel, checked.messageId(), checked.event().payload().toNotification());
    }
}
