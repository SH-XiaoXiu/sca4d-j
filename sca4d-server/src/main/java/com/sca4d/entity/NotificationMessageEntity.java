package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.notification.NotificationCategory;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 通知消息实体。
 */
@Data
@Accessors(chain = true)
@Table("notification_message")
public class NotificationMessageEntity {

    private Long id;
    private String title;
    private String content;
    private NotificationCategory category;
    private PrincipalType senderPrincipalType;
    private Long senderPrincipalId;
    private String payloadJson;
    private OffsetDateTime createdAt;
    private Integer deleted;
}
