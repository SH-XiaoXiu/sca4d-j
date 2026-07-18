package com.sca4d.notification;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 当前用户通知业务对象。
 */
@Data
@Accessors(chain = true)
public class NotificationMessage {

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
}
