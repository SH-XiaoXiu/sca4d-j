package com.sca4d.plugin.runtime.exception;

/** 插件运行时的故障，例如插件加载、重载或资源关闭失败。 */
public class PluginException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public PluginException(String message) {
        super(message);
    }
}
