package com.sca4d.auth.email;

import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.notification.email.EmailChannels;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次 Email Code 通知需要投递的 Account、验证码和有效分钟数。 */
public final class EmailCodeNotice {

    /** Email Code 使用 Email 必发渠道的类型化通知定义。 */
    public static final @NotNull NotificationType<EmailCodeNotice> TYPE = NotificationType.define(
            "authentication.email-code",
            EmailCodeNotice.class,
            definition -> {
                definition.required(EmailChannels.EMAIL);
                definition.variable("code", (notice, locale) -> notice.code());
                definition.variable(
                        "expiresInMinutes",
                        (notice, locale) -> Long.toString(notice.expiresInMinutes()));
            },
            (notice, execution) -> execution.sendToAccount(notice.accountId()));

    private final long accountId;

    private final @NotNull String code;

    private final long expiresInMinutes;

    /** 创建只在通知入队期间存在的 Email 验证码领域对象。 */
    public EmailCodeNotice(long accountId, @NotNull String code, long expiresInMinutes) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Email Code Account 标识必须为正数");
        }
        if (expiresInMinutes <= 0) {
            throw new IllegalArgumentException("Email Code 有效分钟数必须为正数");
        }
        this.accountId = accountId;
        this.code = Objects.requireNonNull(code, "Email 验证码不能为空");
        this.expiresInMinutes = expiresInMinutes;
    }

    /** @return 接收验证码的 Account 标识 */
    @Contract(pure = true)
    public long accountId() {
        return accountId;
    }

    /** @return 只交给通知变量解析器的验证码 */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    /** @return 模板显示的验证码有效分钟数 */
    @Contract(pure = true)
    public long expiresInMinutes() {
        return expiresInMinutes;
    }
}
