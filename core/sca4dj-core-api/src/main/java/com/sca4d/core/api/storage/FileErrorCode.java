package com.sca4d.core.api.storage;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** 表示文件申请、完成、绑定、读取和存储不可用的失败结果。 */
@ErrorCodeRange(start = 2500, end = 2599)
public enum FileErrorCode implements ErrorCode {
    /** 文件标识、上传申请或对象内容不存在有效值。 */
    INVALID("2500", ErrorCategory.VALIDATION, "error.storage.file-invalid"),
    /** 文件不存在，或者调用方不应得知它是否存在。 */
    NOT_FOUND("2501", ErrorCategory.NOT_FOUND, "error.storage.file-not-found"),
    /** 上传地址已经超过申请时确定的有效期。 */
    UPLOAD_EXPIRED("2502", ErrorCategory.CONFLICT, "error.storage.upload-expired"),
    /** 当前文件状态或声明用途不允许执行请求动作。 */
    STATE_CONFLICT("2503", ErrorCategory.CONFLICT, "error.storage.state-conflict"),
    /** 实际媒体类型或字节数与上传申请不一致。 */
    CONTENT_MISMATCH("2504", ErrorCategory.VALIDATION, "error.storage.content-mismatch"),
    /** 文件记录对应的存储实现未注册或当前不可用。 */
    BACKEND_UNAVAILABLE("2505", ErrorCategory.UNAVAILABLE, "error.storage.backend-unavailable"),
    /** 本地文件传输 Token 无效、动作不符或已经到期。 */
    TRANSFER_INVALID("2506", ErrorCategory.VALIDATION, "error.storage.transfer-invalid");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    FileErrorCode(@NotNull String code, @NotNull ErrorCategory category, @NotNull String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    @Override
    public @NotNull String code() {
        return code;
    }

    @Override
    public @NotNull ErrorCategory category() {
        return category;
    }

    @Override
    public @NotNull String messageKey() {
        return messageKey;
    }
}
