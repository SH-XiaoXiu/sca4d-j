package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** 返回一个文件的短期读取地址。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class FileAccessUrl {
    private final long fileId;

    private final @NotNull URI url;

    private final @NotNull Instant expiresAt;

    /**
     * 创建文件读取地址。
     *
     * @param fileId 正数文件标识
     * @param url 客户端可以访问的绝对地址
     * @param expiresAt 地址失效时间
     * @throws IllegalArgumentException 文件标识不是正数或地址不是绝对地址时抛出
     */
    public FileAccessUrl(long fileId, @NotNull URI url, @NotNull Instant expiresAt) {
        if (fileId <= 0) {
            throw new IllegalArgumentException("文件标识必须为正数");
        }
        this.fileId = fileId;
        URI checkedUrl = Objects.requireNonNull(url, "读取地址不能为空");
        if (!checkedUrl.isAbsolute())
            throw new IllegalArgumentException("读取地址必须是绝对地址");
        this.url = checkedUrl;
        this.expiresAt = Objects.requireNonNull(expiresAt, "读取到期时间不能为空");
    }

    /**
     * 返回文件标识。
     *
     * @return 正数文件标识
     */
    public long fileId() {
        return fileId;
    }

    /**
     * 返回客户端可以访问的短期地址。
     *
     * @return 绝对读取地址
     */
    public @NotNull URI url() {
        return url;
    }

    /**
     * 返回读取地址失效时间。
     *
     * @return 地址失效时间
     */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
