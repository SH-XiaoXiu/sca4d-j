package com.sca4d.audit.exception;

/** 审计模块的运行时故障，例如审计载荷编解码失败。 */
public class AuditException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public AuditException(String message) {
        super(message);
    }
}
