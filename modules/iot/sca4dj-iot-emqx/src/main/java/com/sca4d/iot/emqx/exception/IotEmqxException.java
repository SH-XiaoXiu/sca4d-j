package com.sca4d.iot.emqx.exception;

/** 表示 IoT EMQX 模块的技术失败，例如依赖不可用、数据格式损坏或运行环境缺失，保留原始原因供排障。 */
public class IotEmqxException extends RuntimeException {

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public IotEmqxException(String message) {
        super(message);
    }

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public IotEmqxException(String message, Throwable cause) {
        super(message, cause);
    }
}
