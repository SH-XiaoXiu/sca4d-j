package com.sca4d.iot.exception;

/** Timescale 时序存储的依赖或数据故障，例如事件记录缺少必要字段。 */
public class IotTimescaleException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public IotTimescaleException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public IotTimescaleException(String message) {
        super(message);
    }
}
