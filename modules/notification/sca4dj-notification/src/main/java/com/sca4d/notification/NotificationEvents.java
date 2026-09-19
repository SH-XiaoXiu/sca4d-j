package com.sca4d.notification;

import com.sca4d.core.api.event.EventType;
import org.jetbrains.annotations.NotNull;

/** 声明 Notification 模块发布和消费的稳定事件类型。 */
public final class NotificationEvents {

    /** 异步发送一项已经冻结路由事实的类型化通知。 */
    public static final @NotNull EventType<NotificationRequested> REQUESTED = new EventType<>(
            "notification.requested",
            2,
            NotificationRequested.class);

    private NotificationEvents() {
    }
}
