package com.sca4d.identity.ldap;

import com.sca4d.identity.IdentityProviderScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 读取和替换 LDAP 地址、搜索、属性映射与加密服务账号密码配置。 */
public interface LdapProviderService {

    /** @return 指定 LDAP Provider 的安全配置；尚未配置时抛出业务异常 */
    @NotNull
    LdapProviderConfiguration configuration(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId);

    /**
     * 创建或完整替换停用 Provider 的 LDAP 配置。
     *
     * @param serviceAccountPassword 新服务账号密码；更新时为 null 表示保留现有密文
     * @return 已保存配置及推进后的父 Provider 版本
     */
    @NotNull
    LdapProviderConfiguration replace(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull String url,
            @NotNull String baseDn,
            @Nullable String serviceAccountDn,
            @Nullable String serviceAccountPassword,
            @NotNull String userSearchBase,
            @NotNull String userSearchFilter,
            @NotNull String externalSubjectAttribute,
            @Nullable String displayNameAttribute,
            @Nullable String emailAttribute,
            @NotNull LdapTransportSecurity transportSecurity,
            long version);

    /** 验证已保存 LDAP 配置能够完成 TLS 协商、服务账号 bind 和 base DN 读取。 */
    void testConnection(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId);
}
