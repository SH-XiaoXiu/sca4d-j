package com.sca4d.notification;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.delivery.DeliveryOutcome;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationDelivery;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 校验每个渠道只有一个投递实现，并按冻结渠道执行同步或独立消费。
 */
public final class NotificationDeliveryRouter {

    private final @NotNull Map<@NotNull TreeCode, @NotNull NotificationDelivery> deliveries;

    /**
     * @param deliveries 应用安装的通知渠道投递实现
     */
    public NotificationDeliveryRouter(@NotNull Collection<? extends NotificationDelivery> deliveries) {
        Map<TreeCode, NotificationDelivery> checked = new LinkedHashMap<>();
        for (NotificationDelivery delivery : Objects.requireNonNull(deliveries, "通知投递实现不能为空")) {
            NotificationDelivery value = Objects.requireNonNull(delivery, "通知投递实现不能包含 null");
            if (checked.putIfAbsent(value.channel(), value) != null) {
                throw new IllegalArgumentException("重复通知渠道投递实现: " + value.channel());
            }
        }
        this.deliveries = Map.copyOf(checked);
    }

    /**
     * 判断指定渠道当前是否安装并能处理通知。
     */
    @CheckReturnValue
    public boolean available(@NotNull TreeCode channel, @NotNull Notification notification) {
        NotificationDelivery delivery = deliveries.get(Objects.requireNonNull(channel, "通知渠道不能为空"));
        return delivery != null && delivery.available(Objects.requireNonNull(notification, "通知不能为空"));
    }

    /**
     * 同步执行通知已经选中的全部渠道。
     */
    public void deliver(@NotNull MessageId messageId, @NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        for (TreeCode channel : checked.channels()) {
            DeliveryOutcome outcome = deliver(channel, messageId, checked);
            if (outcome.status() != DeliveryOutcome.Status.CONFIRMED) {
                throw new IllegalStateException("同步通知渠道没有确认投递: " + channel + '/' + outcome.status());
            }
        }
    }

    /**
     * 只执行一个 Consumer 自己负责的渠道。
     */
    public @NotNull DeliveryOutcome deliver(
            @NotNull TreeCode channel,
            @NotNull MessageId messageId,
            @NotNull Notification notification) {
        TreeCode checkedChannel = Objects.requireNonNull(channel, "通知渠道不能为空");
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        if (!checked.channels().contains(checkedChannel)) {
            return DeliveryOutcome.confirmed();
        }
        NotificationDelivery delivery = deliveries.get(checkedChannel);
        if (delivery == null || !delivery.available(checked)) {
            throw new IllegalStateException("通知渠道在消费时不可用: " + checkedChannel);
        }
        return delivery.deliver(Objects.requireNonNull(messageId, "通知消息标识不能为空"), checked);
    }
}
