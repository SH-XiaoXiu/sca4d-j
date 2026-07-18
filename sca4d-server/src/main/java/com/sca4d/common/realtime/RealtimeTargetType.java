package com.sca4d.common.realtime;

/**
 * 实时消息目标类型。
 */
public enum RealtimeTargetType {
    /** 登录身份目标。 */
    SUBJECT,
    /** 业务执行主体目标。 */
    PRINCIPAL,
    /** 租户目标。 */
    TENANT,
    /** 公共主题目标。 */
    TOPIC,
    /** 单连接目标。 */
    CONNECTION
}
