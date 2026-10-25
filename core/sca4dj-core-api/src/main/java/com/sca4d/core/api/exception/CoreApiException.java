package com.sca4d.core.api.exception;

/** 核心接口执行公共契约时遇到的运行时故障，例如序列化、流程或设置数据不符合预期。 */
public class CoreApiException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public CoreApiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public CoreApiException(String message) {
        super(message);
    }
}
