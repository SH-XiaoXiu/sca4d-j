package com.sca4d.im.error;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/**
 * IM 模块错误码。
 */
@CodeRange(start = 3100, end = 3199)
public enum ImErrorCode implements BizErrorCode {
    /** 会话不存在。 */
    CONVERSATION_NOT_FOUND(3100, "error.im.conversation-not-found", ResponseCode.NOT_FOUND),
    /** 消息不存在。 */
    MESSAGE_NOT_FOUND(3101, "error.im.message-not-found", ResponseCode.NOT_FOUND),
    /** 当前主体不是会话成员。 */
    NOT_CONVERSATION_MEMBER(3102, "error.im.not-conversation-member", ResponseCode.FORBIDDEN),
    /** 消息不能撤回。 */
    MESSAGE_REVOKE_FORBIDDEN(3103, "error.im.message-revoke-forbidden", ResponseCode.FORBIDDEN),
    /** 请求没有提供会话主键。 */
    CONVERSATION_ID_REQUIRED(3104, "error.im.conversation-id-required", ResponseCode.BAD_REQUEST),
    /** 附件文件没有绑定当前消息资源。 */
    FILE_RESOURCE_ID_INVALID(3105, "error.im.file-resource-id-invalid", ResponseCode.BAD_REQUEST),
    /** 图片消息没有提供附件文件。 */
    IMAGE_ATTACHMENT_REQUIRED(3106, "error.im.image-attachment-required", ResponseCode.BAD_REQUEST),
    /** 图片消息没有提供对应的上传占用授权。 */
    IMAGE_UPLOAD_GRANT_REQUIRED(3107, "error.im.image-upload-grant-required", ResponseCode.BAD_REQUEST),
    /** 实际会话成员数量与请求声明不一致。 */
    MEMBER_COUNT_MISMATCH(3108, "error.im.member-count-mismatch", ResponseCode.BAD_REQUEST),
    /** 会话成员没有提供 Principal 主键。 */
    MEMBER_PRINCIPAL_REQUIRED(3109, "error.im.member-principal-required", ResponseCode.BAD_REQUEST),
    /** 会话成员不属于同一个租户或平台空间。 */
    MEMBER_WORKSPACE_MISMATCH(3110, "error.im.member-workspace-mismatch", ResponseCode.BAD_REQUEST),
    /** 创建会话时提供的成员数量少于最低要求。 */
    MEMBERS_TOO_FEW(3111, "error.im.members-too-few", ResponseCode.BAD_REQUEST),
    /** 文本消息没有正文且没有允许的附件。 */
    MESSAGE_CONTENT_REQUIRED(3112, "error.im.message-content-required", ResponseCode.BAD_REQUEST),
    /** 请求没有提供消息主键。 */
    MESSAGE_ID_REQUIRED(3113, "error.im.message-id-required", ResponseCode.BAD_REQUEST),
    /** Principal 不属于请求声明的 Subject。 */
    PRINCIPAL_SUBJECT_MISMATCH(3114, "error.im.principal-subject-mismatch", ResponseCode.FORBIDDEN);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    ImErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
