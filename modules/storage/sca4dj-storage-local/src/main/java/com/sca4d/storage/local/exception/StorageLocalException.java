package com.sca4d.storage.local.exception;

/** 本地文件存储的依赖故障，例如存储根目录不可用。 */
public class StorageLocalException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public StorageLocalException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public StorageLocalException(String message) {
        super(message);
    }
}
