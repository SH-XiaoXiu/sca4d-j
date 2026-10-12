package com.sca4d.core.api.retry;

/** 表示共同重试判断停止的原因，由所属模块映射业务终态。 */
public enum RetryStopReason {
    /** 规则只允许首次执行。 */
    DISABLED,
    /** 当前失败不符合再次尝试条件。 */
    NOT_RETRYABLE,
    /** 包含首次执行的总次数已经耗尽。 */
    ATTEMPTS_EXHAUSTED
}
