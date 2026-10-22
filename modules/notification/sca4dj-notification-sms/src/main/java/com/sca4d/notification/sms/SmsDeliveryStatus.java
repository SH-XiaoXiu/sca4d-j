package com.sca4d.notification.sms;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示 SMS Provider API 对一次提交给出的确定程度。 */
public enum SmsDeliveryStatus implements BaseEnum {
    /** 已登记尝试，Provider 调用尚未给出结果。 */
    PENDING("等待提供方结果"),
    /** Provider 已明确接受请求，不表示手机最终送达。 */
    ACCEPTED("提供方已接受"),
    /** Provider 查询确认手机已经收到短信。 */
    DELIVERED("已送达"),
    /** Provider 明确拒绝请求，可以按策略尝试下一候选。 */
    REJECTED("提供方已拒绝"),
    /** Provider 查询确认短信最终失败。 */
    FAILED("投递失败"),
    /** 传输中断导致结果未知，不能自动换平台避免重复短信。 */
    UNKNOWN("投递结果未知");

    private final @NotNull String description;

    SmsDeliveryStatus(@NotNull String description) {
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
