package com.sca4d.auth.email.challenge;

/** 表示 Redis Email Code Challenge 是否仍可验证、已消费或已耗尽。 */
public enum EmailCodeChallengeStatus {
    /** 仍可提交验证码。 */
    PENDING,
    /** 已经成功消费。 */
    CONSUMED,
    /** 错误次数达到上限。 */
    EXHAUSTED
}
