package com.sca4d.iam.exception;

/** 身份与访问管理的依赖或数据故障，例如 RBAC 数据或权限缓存结果异常。 */
public class IamException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public IamException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public IamException(String message) {
        super(message);
    }
}
