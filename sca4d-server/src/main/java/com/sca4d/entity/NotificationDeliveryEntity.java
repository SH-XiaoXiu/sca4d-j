package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.notification.NotificationDeliveryStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 通知投递实体。
 */
@Data
@Accessors(chain = true)
@Table("notification_delivery")
public class NotificationDeliveryEntity {

    private Long id;
    private Long notificationId;
    private Long subjectId;
    private PrincipalType principalType;
    private Long principalId;
    private NotificationDeliveryStatus status;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
