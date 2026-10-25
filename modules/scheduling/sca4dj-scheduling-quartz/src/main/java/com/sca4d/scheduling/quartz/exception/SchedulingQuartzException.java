package com.sca4d.scheduling.quartz.exception;

/** Quartz 调度的故障，例如计划定义缺失或调度器操作失败。 */
public class SchedulingQuartzException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public SchedulingQuartzException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public SchedulingQuartzException(String message) {
        super(message);
    }
}
