package com.sca4d.notification.view;

import com.sca4d.notification.NotificationCategory;
import com.sca4d.notification.NotificationDeliveryStatus;
import com.sca4d.notification.NotificationMessage;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/**
 * 通知列表视图。
 */
@Data
@Accessors(chain = true)
public class NotificationView {

    private Long notificationId;
    private Long deliveryId;
    private String title;
    private String content;
    private NotificationCategory category;
    private NotificationDeliveryStatus status;
    private String payloadJson;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;

    /**
     * 从通知业务对象创建视图。
     *
     * @param message 通知业务对象
     * @return 通知视图
     */
    public static @NotNull NotificationView from(@NotNull NotificationMessage message) {
        return new NotificationView()
                .setNotificationId(message.getNotificationId())
                .setDeliveryId(message.getDeliveryId())
                .setTitle(message.getTitle())
                .setContent(message.getContent())
                .setCategory(message.getCategory())
                .setStatus(message.getStatus())
                .setPayloadJson(message.getPayloadJson())
                .setDeliveredAt(message.getDeliveredAt())
                .setReadAt(message.getReadAt())
                .setCreatedAt(message.getCreatedAt());
    }
}
