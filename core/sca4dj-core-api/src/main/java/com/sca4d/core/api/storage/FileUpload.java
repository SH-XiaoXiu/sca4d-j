package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 返回客户端可以原样执行的文件上传请求。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class FileUpload {
    private final long fileId;

    private final @NotNull String method;

    private final @NotNull URI url;

    private final @NotNull Instant expiresAt;

    private final @NotNull Map<@NotNull String, @NotNull String> headers;

    /**
     * 创建客户端必须原样执行的上传请求。
     *
     * @param fileId 正数文件标识
     * @param method 非空白 HTTP 方法
     * @param url 客户端可以访问的绝对上传地址
     * @param headers 客户端必须原样发送的请求头
     * @param expiresAt 上传地址失效时间
     * @throws IllegalArgumentException 文件标识、方法或地址无效时抛出
     */
    public FileUpload(
            long fileId,
            @NotNull String method,
            @NotNull URI url,
            @NotNull Map<@NotNull String, @NotNull String> headers,
            @NotNull Instant expiresAt) {
        if (fileId <= 0) {
            throw new IllegalArgumentException("文件标识必须为正数");
        }
        this.fileId = fileId;
        String checkedMethod = Objects.requireNonNull(method, "上传方法不能为空").trim();
        if (checkedMethod.isEmpty())
            throw new IllegalArgumentException("上传方法不能为空");
        URI checkedUrl = Objects.requireNonNull(url, "上传地址不能为空");
        if (!checkedUrl.isAbsolute())
            throw new IllegalArgumentException("上传地址必须是绝对地址");
        this.method = checkedMethod;
        this.url = checkedUrl;
        this.headers = Map.copyOf(Objects.requireNonNull(headers, "上传请求头不能为空"));
        this.expiresAt = Objects.requireNonNull(expiresAt, "上传到期时间不能为空");
    }

    /**
     * 文件标识。
     *
     * @return 正数文件标识
     */
    public long fileId() {
        return fileId;
    }

    /**
     * 上传使用的 HTTP 方法。
     *
     * @return 非空白 HTTP 方法
     */
    public @NotNull String method() {
        return method;
    }

    /**
     * 客户端可以访问的上传地址。
     *
     * @return 绝对上传地址
     */
    public @NotNull URI url() {
        return url;
    }

    /**
     * 客户端必须原样发送的请求头。
     *
     * @return 不可修改的请求头
     */
    public @NotNull Map<@NotNull String, @NotNull String> headers() {
        return headers;
    }

    /**
     * 上传地址失效时间。
     *
     * @return 地址失效时间
     */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
