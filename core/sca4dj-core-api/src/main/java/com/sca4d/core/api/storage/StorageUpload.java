package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** 表示存储实现生成的统一 PUT 请求。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class StorageUpload {
    private final @NotNull URI url;

    private final @NotNull Map<@NotNull String, @NotNull String> headers;

    /**
     * 创建存储实现生成的上传请求。
     *
     * @param url 客户端可以访问的绝对上传地址
     * @param headers 客户端必须原样发送的请求头
     * @throws IllegalArgumentException 地址不是绝对地址时抛出
     */
    public StorageUpload(@NotNull URI url, @NotNull Map<@NotNull String, @NotNull String> headers) {
        URI checkedUrl = Objects.requireNonNull(url, "上传地址不能为空");
        if (!checkedUrl.isAbsolute())
            throw new IllegalArgumentException("上传地址必须是绝对地址");
        this.url = checkedUrl;
        this.headers = Map.copyOf(Objects.requireNonNull(headers, "上传请求头不能为空"));
    }

    /**
     * 返回客户端可以访问的上传地址。
     *
     * @return 绝对上传地址
     */
    public @NotNull URI url() {
        return url;
    }

    /**
     * 返回客户端必须原样发送的请求头。
     *
     * @return 不可修改的请求头
     */
    public @NotNull Map<@NotNull String, @NotNull String> headers() {
        return headers;
    }
}
