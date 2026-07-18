package com.sca4d.im;

import com.sca4d.common.auth.PrincipalType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * IM 消息业务对象。
 */
@Data
@Accessors(chain = true)
public class ImMessage {

    private Long messageId;
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
}
