package com.sca4d.im.view;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.file.view.FileSignatureRefView;
import com.sca4d.im.ImMessage;
import com.sca4d.im.ImMessageContentType;
import com.sca4d.im.ImMessageStatus;
import com.sca4d.im.signature.ImFileSignaturePolicy;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/**
 * IM 消息视图。
 */
@Data
@Accessors(chain = true)
public class ImMessageView {

    private Long messageId;
    private Long conversationId;
    private PrincipalType senderPrincipalType;
    private Long senderPrincipalId;
    private Long senderSubjectId;
    private ImMessageContentType contentType;
    private String content;
    private FileSignatureRefView attachment;
    private String payloadJson;
    private ImMessageStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;

    /**
     * 从消息业务对象创建视图。
     *
     * @param message 消息业务对象
     * @return 消息视图
     */
    public static @NotNull ImMessageView from(@NotNull ImMessage message) {
        return new ImMessageView()
                .setMessageId(message.getMessageId())
                .setConversationId(message.getConversationId())
                .setSenderPrincipalType(message.getSenderPrincipalType())
                .setSenderPrincipalId(message.getSenderPrincipalId())
                .setSenderSubjectId(message.getSenderSubjectId())
                .setContentType(message.getContentType())
                .setContent(message.getContent())
                .setAttachment(attachment(message))
                .setPayloadJson(message.getPayloadJson())
                .setStatus(message.getStatus())
                .setCreatedAt(message.getCreatedAt())
                .setRevokedAt(message.getRevokedAt());
    }

    /**
     * 将消息附件主键转换为前端共用批量换签使用的文件引用。
     *
     * @param message 已完成业务查询的 IM 消息
     * @return 绑定消息场景与消息主键的文件引用；消息没有附件时返回 {@code null}
     */
    private static @Nullable FileSignatureRefView attachment(@NotNull ImMessage message) {
        if (message.getAttachmentFileId() == null) {
            return null;
        }
        return new FileSignatureRefView()
                .setScene(ImFileSignaturePolicy.MESSAGE_ATTACHMENT)
                .setResourceId(message.getMessageId().toString())
                .setFileId(message.getAttachmentFileId());
    }
}
