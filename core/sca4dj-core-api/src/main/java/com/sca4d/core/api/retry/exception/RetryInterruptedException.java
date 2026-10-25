package com.sca4d.core.api.retry.exception;

/** 表示同步重试等待或执行被中断，线程中断标记保持有效。 */
public class RetryInterruptedException extends RuntimeException {
    /** @param cause 中断原因 */
    public RetryInterruptedException(Throwable cause) {
        super("同步重试已中断", cause);
    }
}
