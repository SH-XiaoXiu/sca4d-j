package com.sca4d.auth.exception;

/** 认证模块的运行时故障，例如 Session 编解码、资源装载或持久化数据损坏。 */
public class AuthException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public AuthException(String message) {
        super(message);
    }
}
