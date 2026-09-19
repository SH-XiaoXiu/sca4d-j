package com.sca4d.notification.sms;

/** 表示 SMS Provider API 对一次提交给出的确定程度。 */
public enum SmsDeliveryStatus {
    /** 已登记尝试，Provider 调用尚未给出结果。 */
    PENDING,
    /** Provider 已明确接受请求，不表示手机最终送达。 */
    ACCEPTED,
    /** Provider 明确拒绝请求，可以按策略尝试下一候选。 */
    REJECTED,
    /** 传输中断导致结果未知，不能自动换平台避免重复短信。 */
    UNKNOWN
}
