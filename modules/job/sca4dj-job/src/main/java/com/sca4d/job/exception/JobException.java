package com.sca4d.job.exception;

/** 后台任务模块的运行时故障，例如任务定义、执行队列或序列化失败。 */
public class JobException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public JobException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public JobException(String message) {
        super(message);
    }
}
