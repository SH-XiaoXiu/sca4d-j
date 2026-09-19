package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.time.Instant;

/** 为必须经过 App 传输内容的存储实现生成短期签名地址。 */
public interface StorageTransferSigner {
    /**
     * 为本地 PUT 生成带短期 Token 的绝对地址。
     *
     * @param fileId 正数文件标识
     * @param expiresAt Token 失效时间
     * @return 客户端可以访问的绝对上传地址
     */
    @NotNull
    URI uploadUrl(long fileId, @NotNull Instant expiresAt);

    /**
     * 为本地 GET 生成带短期 Token 的绝对地址。
     *
     * @param fileId 正数文件标识
     * @param expiresAt Token 失效时间
     * @return 客户端可以访问的绝对读取地址
     */
    @NotNull
    URI accessUrl(long fileId, @NotNull Instant expiresAt);
}
