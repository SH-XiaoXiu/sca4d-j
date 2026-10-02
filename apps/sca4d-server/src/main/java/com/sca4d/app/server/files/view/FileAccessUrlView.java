package com.sca4d.app.server.files.view;

import com.sca4d.core.api.storage.FileAccessUrl;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回一个已经由 App 授权文件的短期读取地址。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class FileAccessUrlView {

    private final @NotNull Instant expiresAt;

    private final @NotNull String url;

    private final long fileId;

    private FileAccessUrlView(long fileId, @NotNull String url, @NotNull Instant expiresAt) {
        this.fileId = fileId;
        this.url = url;
        this.expiresAt = expiresAt;
    }

    /**
     * 从文件服务结果创建 HTTP 视图。
     *
     * @param access 文件服务返回的读取地址
     * @return 不包含存储实现信息的 HTTP 视图
     */
    @CheckReturnValue
    public static @NotNull FileAccessUrlView from(@NotNull FileAccessUrl access) {
        FileAccessUrl checked = Objects.requireNonNull(access, "文件读取地址不能为空");
        return new FileAccessUrlView(
                checked.fileId(),
                checked.url().toString(),
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
     * 返回客户端可以访问的短期读取地址。
     *
     * @return 客户端可以访问的短期读取地址
     */
    public @NotNull String getUrl() {
        return url;
    }

    /**
     * 返回读取地址到期时间。
     *
     * @return 读取地址到期时间
     */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
}
