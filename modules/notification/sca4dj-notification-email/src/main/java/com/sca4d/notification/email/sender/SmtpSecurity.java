package com.sca4d.notification.email.sender;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 声明 SMTP 连接使用明文、STARTTLS 升级或隐式 TLS。 */
public enum SmtpSecurity implements BaseEnum {
    /** 不启用 TLS，适合受信任内网或本地测试服务。 */
    NONE("明文连接"),
    /** 建立 SMTP 连接后要求使用 STARTTLS 升级。 */
    STARTTLS("STARTTLS"),
    /** 从连接开始使用隐式 TLS。 */
    TLS("TLS");

    private final @NotNull String description;

    SmtpSecurity(@NotNull String description) {
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
