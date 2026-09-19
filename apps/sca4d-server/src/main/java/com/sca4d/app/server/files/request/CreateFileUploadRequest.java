package com.sca4d.app.server.files.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收客户端为目标 Platform Account 申请头像上传时提交的精确内容信息。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class CreateFileUploadRequest {

    private long targetAccountId;

    @NotBlank
    @Size(max = 255)
    private @Nullable String filename;

    @NotBlank
    @Size(max = 128)
    private @Nullable String contentType;

    private long sizeBytes;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public CreateFileUploadRequest() {
    }

    /**
     * 返回头像所属的目标 Platform Account 标识。
     *
     * @return 正数 Account 标识
     */
    public long getTargetAccountId() {
        return targetAccountId;
    }

    /**
     * 设置头像所属的目标 Platform Account 标识。
     *
     * @param targetAccountId 正数 Account 标识
     */
    public void setTargetAccountId(long targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    /**
     * 返回原始文件名。
     *
     * @return 原始文件名
     */
    public @NotNull String getFilename() {
        return Objects.requireNonNull(filename, "文件名不能为空");
    }

    /**
     * 设置原始文件名。
     *
     * @param filename 原始文件名
     */
    public void setFilename(@Nullable String filename) {
        this.filename = filename;
    }

    /**
     * 返回客户端声明的媒体类型。
     *
     * @return 声明媒体类型
     */
    public @NotNull String getContentType() {
        return Objects.requireNonNull(contentType, "文件媒体类型不能为空");
    }

    /**
     * 设置客户端声明的媒体类型。
     *
     * @param contentType 声明媒体类型
     */
    public void setContentType(@Nullable String contentType) {
        this.contentType = contentType;
    }

    /**
     * 返回客户端声明的精确文件字节数。
     *
     * @return 精确文件字节数
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * 设置客户端声明的精确文件字节数。
     *
     * @param sizeBytes 精确文件字节数
     */
    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
