package com.sca4d.mail;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** SMTP 连接建立时使用的传输安全方式。 */
public enum SmtpSecurity implements BaseEnum {
    /** 直接使用未加密 SMTP 连接，通常只用于受控内网或本地 Mailpit。 */
    NONE("NONE", "不加密"),
    /** 建立 SMTP 连接后要求服务器升级到 TLS。 */
    STARTTLS("STARTTLS", "STARTTLS"),
    /** 从连接开始即使用 TLS，通常对应 SMTPS 端口。 */
    IMPLICIT_TLS("IMPLICIT_TLS", "TLS");

    private final String value;
    private final String description;

    SmtpSecurity(@NotNull String value, @NotNull String description) {
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
