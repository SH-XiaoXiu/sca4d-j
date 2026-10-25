package com.sca4d.plugin.spring.exception;

/** 插件与 Spring 集成的故障，例如 App API 描述缺失。 */
public class PluginSpringException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public PluginSpringException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public PluginSpringException(String message) {
        super(message);
    }
}
