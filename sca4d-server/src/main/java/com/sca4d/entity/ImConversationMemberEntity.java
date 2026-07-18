package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.im.ImMemberRole;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * IM 会话成员实体。
 */
@Data
@Accessors(chain = true)
@Table("im_conversation_member")
public class ImConversationMemberEntity {

    private Long id;
    private Long conversationId;
    private PrincipalType principalType;
    private Long principalId;
    private Long subjectId;
    private String displayName;
    private ImMemberRole memberRole;
    private Long lastReadMessageId;
    private OffsetDateTime lastReadAt;
    private OffsetDateTime joinedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
