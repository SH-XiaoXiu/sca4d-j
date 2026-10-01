package com.sca4d.app.server.files.view;

import com.sca4d.core.api.storage.FileUpload;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 返回客户端必须原样执行的短期上传请求。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class FileUploadView {

    private final long fileId;

    private final @NotNull String method;

    private final @NotNull String url;

    private final @NotNull Map<@NotNull String, @NotNull String> headers;

    private final @NotNull Instant expiresAt;

    private FileUploadView(
            long fileId,
            @NotNull String method,
            @NotNull String url,
            @NotNull Map<@NotNull String, @NotNull String> headers,
            @NotNull Instant expiresAt) {
        this.fileId = fileId;
        this.method = method;
        this.url = url;
        this.headers = Map.copyOf(headers);
        this.expiresAt = expiresAt;
    }

    /**
     * 从文件服务结果创建 HTTP 视图。
     *
     * @param upload 文件服务返回的上传请求
     * @return 不包含存储实现信息的 HTTP 视图
     */
    @CheckReturnValue
    public static @NotNull FileUploadView from(@NotNull FileUpload upload) {
        FileUpload checked = Objects.requireNonNull(upload, "文件上传请求不能为空");
        return new FileUploadView(
                checked.fileId(),
                checked.method(),
                checked.url().toString(),
                checked.headers(),
                checked.expiresAt());
    }

    /**
     * 返回文件标识。
     *
     * @return 文件标识
     */
    public long getFileId() {
        return fileId;
    }

    /**
     * 返回客户端必须使用的 HTTP 方法。
     *
     * @return 客户端必须使用的 HTTP 方法
     */
    public @NotNull String getMethod() {
        return method;
    }

    /**
     * 返回客户端可以访问的短期上传地址。
     *
     * @return 客户端可以访问的短期上传地址
     */
    public @NotNull String getUrl() {
        return url;
    }

    /**
     * 返回客户端必须原样发送的请求头。
     *
     * @return 客户端必须原样发送的不可修改请求头
     */
    public @NotNull @Unmodifiable Map<@NotNull String, @NotNull String> getHeaders() {
        return headers;
    }

    /**
     * 返回上传地址到期时间。
     *
     * @return 上传地址到期时间
     */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
}
