package com.sca4d.auth.sms.challenge;

/** 表示 SMS Code Challenge 是否仍可提交。 */
public enum SmsCodeChallengeStatus {
    PENDING,
    CONSUMED,
    EXHAUSTED
}
