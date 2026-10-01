package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** 返回由 HTTP 路由或服务端业务读取并关闭的文件内容。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class FileContent implements AutoCloseable {
    private final @NotNull InputStream stream;

    private final @NotNull String contentType;

    private final long sizeBytes;

    /**
     * 创建需要由 App 传输的文件内容。
     *
     * @param stream 由调用方读取并关闭的文件输入流
     * @param contentType HTTP 响应使用的媒体类型
     * @param sizeBytes 响应的精确正数字节数
     * @throws IllegalArgumentException 字节数不是正数时抛出
     */
    public FileContent(@NotNull InputStream stream, @NotNull String contentType, long sizeBytes) {
        this.stream = Objects.requireNonNull(stream, "文件输入流不能为空");
        this.contentType = Objects.requireNonNull(contentType, "文件媒体类型不能为空");
        if (sizeBytes <= 0)
            throw new IllegalArgumentException("文件字节数必须为正数");
        this.sizeBytes = sizeBytes;
    }

    /**
     * 返回文件输入流。
     *
     * @return 读取完成后必须关闭的输入流
     */
    public @NotNull InputStream stream() {
        return stream;
    }

    /**
     * 返回文件媒体类型。
     *
     * @return HTTP 响应使用的媒体类型
     */
    public @NotNull String contentType() {
        return contentType;
    }

    /**
     * 返回文件的精确字节数。
     *
     * @return 正数字节数
     */
    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
