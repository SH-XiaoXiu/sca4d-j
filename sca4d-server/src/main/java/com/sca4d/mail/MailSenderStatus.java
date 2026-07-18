package com.sca4d.mail;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 邮件发送配置是否允许承担实际邮件投递。 */
public enum MailSenderStatus implements BaseEnum {
    /** 可以发送邮件。 */
    ACTIVE("ACTIVE", "启用"),
    /** 只能编辑或测试连接，不能发送业务邮件。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    MailSenderStatus(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
