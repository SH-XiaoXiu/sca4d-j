package com.sca4d.file.error;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/**
 * 文件模块错误码。
 */
@CodeRange(start = 3200, end = 3299)
public enum FileErrorCode implements BizErrorCode {
    /** 指定文件不存在或已经删除。 */
    FILE_NOT_FOUND(3200, "error.file.not-found", ResponseCode.NOT_FOUND),
    /** 当前 Principal 不是文件所有者且没有业务读取授权。 */
    FILE_FORBIDDEN(3201, "error.file.forbidden", ResponseCode.FORBIDDEN),
    /** 文件当前状态不允许执行请求的操作。 */
    FILE_STATUS_INVALID(3202, "error.file.status-invalid", ResponseCode.CONFLICT),
    /** 文件字节尚未完整写入临时上传位置。 */
    FILE_UPLOAD_INCOMPLETE(3203, "error.file.upload-incomplete", ResponseCode.CONFLICT),
    /** 文件实际大小超过业务场景允许的上限。 */
    FILE_TOO_LARGE(3204, "error.file.too-large", ResponseCode.PAYLOAD_TOO_LARGE),
    /** 文件签名请求使用了未注册的业务场景。 */
    FILE_SIGNATURE_SCENE_UNKNOWN(3205, "error.file.signature-scene-unknown", ResponseCode.FORBIDDEN),
    /** 批量文件签名请求为空、过大或包含无效项目。 */
    FILE_SIGNATURE_BATCH_INVALID(3206, "error.file.signature-batch-invalid", ResponseCode.BAD_REQUEST),
    /** 上传占用授权无效、已过期或与文件及业务资源不匹配。 */
    FILE_UPLOAD_GRANT_INVALID(3207, "error.file.upload-grant-invalid", ResponseCode.FORBIDDEN),
    /** 文件已经被其他业务资源占用。 */
    FILE_ALREADY_CONSUMED(3208, "error.file.already-consumed", ResponseCode.CONFLICT),
    /** 文件服务已关闭，当前请求不能继续。 */
    FILE_DISABLED(3209, "error.file.disabled", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 文件名为空、过长或包含不允许的字符。 */
    FILE_FILENAME_INVALID(3210, "error.file.filename-invalid", ResponseCode.BAD_REQUEST),
    /** 删除本地文件失败。 */
    FILE_LOCAL_DELETE(3211, "error.file.local-delete", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 读取本地临时文件元数据失败。 */
    FILE_LOCAL_INSPECT(3212, "error.file.local-inspect", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 把本地临时文件移动到正式位置失败。 */
    FILE_LOCAL_PROMOTE(3213, "error.file.local-promote", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 打开本地文件内容失败。 */
    FILE_LOCAL_READ(3214, "error.file.local-read", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 写入本地临时文件失败。 */
    FILE_LOCAL_WRITE(3215, "error.file.local-write", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 检查或创建 MinIO bucket 失败。 */
    FILE_MINIO_BUCKET_INIT(3216, "error.file.minio-bucket-init", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 删除 MinIO 对象失败。 */
    FILE_MINIO_DELETE(3217, "error.file.minio-delete", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 读取 MinIO 对象元数据失败。 */
    FILE_MINIO_METADATA(3218, "error.file.minio-metadata", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 把 MinIO 临时对象移动到正式对象键失败。 */
    FILE_MINIO_PROMOTE(3219, "error.file.minio-promote", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 打开 MinIO 对象内容失败。 */
    FILE_MINIO_READ(3220, "error.file.minio-read", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 生成 MinIO 读取地址失败。 */
    FILE_MINIO_READ_SIGN(3221, "error.file.minio-read-sign", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 生成 MinIO 上传地址失败。 */
    FILE_MINIO_UPLOAD_SIGN(3222, "error.file.minio-upload-sign", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 写入 MinIO 临时对象失败。 */
    FILE_MINIO_WRITE(3223, "error.file.minio-write", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** 文件读取签名项目没有提供 fileId。 */
    FILE_READ_FILE_ID_REQUIRED(3224, "error.file.read-file-id-required", ResponseCode.BAD_REQUEST),
    /** 同一批文件签名请求包含重复 requestId。 */
    FILE_REQUEST_ID_DUPLICATE(3225, "error.file.request-id-duplicate", ResponseCode.BAD_REQUEST),
    /** 文件声明大小或实际大小不是正数。 */
    FILE_SIZE_POSITIVE(3226, "error.file.size-positive", ResponseCode.CONFLICT),
    /** 临时上传已经超过允许完成的时间。 */
    FILE_UPLOAD_EXPIRED(3227, "error.file.upload-expired", ResponseCode.CONFLICT),
    /** 上传请求缺少文件名、媒体类型或大小。 */
    FILE_UPLOAD_METADATA_INCOMPLETE(3228, "error.file.upload-metadata-incomplete", ResponseCode.BAD_REQUEST),
    /** 存储中的实际字节数与创建上传时声明的大小不一致。 */
    FILE_UPLOAD_SIZE_MISMATCH(3229, "error.file.upload-size-mismatch", ResponseCode.CONFLICT),
    /** 文件存储返回了无法归入具体操作的错误。 */
    FILE_STORAGE_ERROR(3298, "error.file.storage", ResponseCode.EXTERNAL_SERVICE_ERROR);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    FileErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
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
