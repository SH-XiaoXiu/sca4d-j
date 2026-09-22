package com.sca4d.auth.sms;

import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.notification.sms.SmsChannels;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次 SMS 验证码通知需要投递的 Account、验证码和有效分钟数。 */
public final class SmsCodeNotice {

    /** SMS Code 只能通过不可关闭的 SMS 渠道发送。 */
    public static final @NotNull NotificationType<SmsCodeNotice> TYPE = NotificationType.define(
            "AUTHENTICATION/SMS_CODE",
            SmsCodeNotice.class,
            definition -> {
                definition.required(SmsChannels.SMS);
                definition.variable("code", (notice, locale) -> notice.code());
                definition.variable("expiresInMinutes", (notice, locale) -> Long.toString(notice.expiresInMinutes()));
            },
            (notice, execution) -> execution.sendToAccount(notice.accountId()));

    private final long accountId;

    private final @NotNull String code;

    private final long expiresInMinutes;

    /** 创建只在通知入队期间存在的 SMS Code 领域对象。 */
    public SmsCodeNotice(long accountId, @NotNull String code, long expiresInMinutes) {
        if (accountId <= 0 || expiresInMinutes <= 0)
            throw new IllegalArgumentException("SMS Code 参数必须为正数");
        this.accountId = accountId;
        this.code = Objects.requireNonNull(code, "SMS 验证码不能为空");
        this.expiresInMinutes = expiresInMinutes;
    }

    /** @return 接收验证码的 Account 标识 */
    public long accountId() {
        return accountId;
    }

    /** @return 只在通知入队前存在的六位验证码 */
    public @NotNull String code() {
        return code;
    }

    /** @return 通知文案展示的有效分钟数 */
    public long expiresInMinutes() {
        return expiresInMinutes;
    }
}
