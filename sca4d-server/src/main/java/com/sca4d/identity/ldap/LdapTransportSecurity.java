package com.sca4d.identity.ldap;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** LDAP 连接建立 TLS 的标准方式。 */
public enum LdapTransportSecurity implements BaseEnum {
    /** 先建立 LDAP 连接，再通过 StartTLS 扩展升级为 TLS。 */
    STARTTLS("STARTTLS", "StartTLS"),
    /** 连接建立时直接使用 LDAPS。 */
    LDAPS("LDAPS", "LDAPS");

    private final String value;
    private final String description;

    LdapTransportSecurity(@NotNull String value, @NotNull String description) {
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
