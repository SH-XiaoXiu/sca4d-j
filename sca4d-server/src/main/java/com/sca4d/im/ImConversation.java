package com.sca4d.im;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * IM 会话业务对象。
 */
@Data
@Accessors(chain = true)
public class ImConversation {

    private Long conversationId;
    private ImConversationType conversationType;
    private String title;
    private Long unreadCount;
    private Long lastMessageId;
    private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime updatedAt;
}
