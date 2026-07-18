package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.im.ImMessageContentType;
import com.sca4d.im.ImMessageStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * IM 消息实体。
 */
@Data
@Accessors(chain = true)
@Table("im_message")
public class ImMessageEntity {

    private Long id;
    private Long conversationId;
    private PrincipalType senderPrincipalType;
    private Long senderPrincipalId;
    private Long senderSubjectId;
    private ImMessageContentType contentType;
    private String content;
    private Long attachmentFileId;
    private String payloadJson;
    private ImMessageStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;
    private Long version;
    private Integer deleted;
}
