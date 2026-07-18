package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.im.ImConversationType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * IM 会话实体。
 */
@Data
@Accessors(chain = true)
@Table("im_conversation")
public class ImConversationEntity {

    private Long id;
    private ImConversationType conversationType;
    private String title;
    private Long lastMessageId;
    private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
