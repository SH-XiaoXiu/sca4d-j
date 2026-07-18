package com.sca4d.identity.ldap;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.entity.IamLdapProviderEntity;
import com.sca4d.identity.IdentityClientStateTargets;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.mapper.LdapProviderMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 读写 LDAP 协议子表，并阻止服务账号密码密文进入管理端对象。 */
@Repository
@RequiredArgsConstructor
public class LdapProviderRepository {

    private final LdapProviderMapper mapper;

    /** @return LDAP 配置及父 Provider 版本；尚未配置时返回 null */
    public @Nullable LdapProviderConfiguration findConfiguration(
            @NotNull Long providerId, long version) {
        IamLdapProviderEntity entity = mapper.selectOneById(providerId);
        if (entity == null) {
            return null;
        }
        return new LdapProviderConfiguration(
                entity.getProviderId(),
                entity.getUrl(),
                entity.getBaseDn(),
                entity.getServiceAccountDn(),
                passwordConfigured(entity),
                entity.getUserSearchBase(),
                entity.getUserSearchFilter(),
                entity.getExternalSubjectAttribute(),
                entity.getDisplayNameAttribute(),
                entity.getEmailAttribute(),
                entity.getTransportSecurity(),
                version);
    }

    /** @return LDAP 认证使用的连接和可选加密服务账号密码；尚未配置时返回 null */
    public @Nullable LdapConnection findConnection(
            @NotNull Long providerId, @NotNull Long providerVersion) {
        IamLdapProviderEntity entity = mapper.selectOneById(providerId);
        if (entity == null
                || (StringUtils.hasText(entity.getServiceAccountDn()) && !passwordConfigured(entity))) {
            return null;
        }
        EncryptedSecret encrypted = passwordConfigured(entity)
                ? new EncryptedSecret(
                        entity.getServiceAccountPasswordKeyId(),
                        entity.getServiceAccountPasswordNonce(),
                        entity.getServiceAccountPasswordCiphertext())
                : null;
        return new LdapConnection(
                entity.getProviderId(),
                entity.getUrl(),
                entity.getBaseDn(),
                entity.getServiceAccountDn(),
                encrypted,
                entity.getUserSearchBase(),
                entity.getUserSearchFilter(),
                entity.getExternalSubjectAttribute(),
                entity.getDisplayNameAttribute(),
                entity.getEmailAttribute(),
                entity.getTransportSecurity(),
                providerVersion);
    }

    /**
     * 新增或替换 LDAP 配置。
     *
     * @param passwordChanged true 时使用 encrypted 替换密码；encrypted 为 null 时清空密码列
     * @param exists 父事务开始时是否已经存在协议记录
     */
    public void saveConfiguration(
            @NotNull LdapProviderConfiguration configuration,
            @Nullable EncryptedSecret encrypted,
            boolean passwordChanged,
            boolean exists) {
        IamLdapProviderEntity entity = new IamLdapProviderEntity()
                .setProviderId(configuration.getProviderId())
                .setUrl(configuration.getUrl())
                .setBaseDn(configuration.getBaseDn())
                .setServiceAccountDn(configuration.getServiceAccountDn())
                .setUserSearchBase(configuration.getUserSearchBase())
                .setUserSearchFilter(configuration.getUserSearchFilter())
                .setExternalSubjectAttribute(configuration.getExternalSubjectAttribute())
                .setDisplayNameAttribute(configuration.getDisplayNameAttribute())
                .setEmailAttribute(configuration.getEmailAttribute())
                .setTransportSecurity(configuration.getTransportSecurity());
        if (encrypted != null) {
            entity.setServiceAccountPasswordKeyId(encrypted.getKeyId())
                    .setServiceAccountPasswordNonce(encrypted.getNonce())
                    .setServiceAccountPasswordCiphertext(encrypted.getCiphertext());
        }
        if (!exists) {
            mapper.insertConfiguration(entity);
            return;
        }
        if (mapper.updateConfiguration(entity, passwordChanged) != 1) {
            throw new ConcurrencyConflictException(
                    IdentityProviderErrorCode.PROVIDER_CHANGED,
                    IdentityClientStateTargets.PROVIDERS);
        }
    }

    /** @return 地址、搜索、external subject 属性及可选服务账号密码组合完整时返回 true */
    public boolean isConfigured(@NotNull Long providerId) {
        IamLdapProviderEntity entity = mapper.selectOneById(providerId);
        return entity != null
                && StringUtils.hasText(entity.getUrl())
                && StringUtils.hasText(entity.getBaseDn())
                && entity.getUserSearchBase() != null
                && StringUtils.hasText(entity.getUserSearchFilter())
                && StringUtils.hasText(entity.getExternalSubjectAttribute())
                && entity.getTransportSecurity() != null
                && (!StringUtils.hasText(entity.getServiceAccountDn()) || passwordConfigured(entity));
    }

    /** @return 三个 AES-GCM 服务账号密码列均存在且不为空时返回 true */
    private boolean passwordConfigured(@NotNull IamLdapProviderEntity entity) {
        return entity.getServiceAccountPasswordKeyId() != null
                && entity.getServiceAccountPasswordNonce() != null
                && entity.getServiceAccountPasswordNonce().length > 0
                && entity.getServiceAccountPasswordCiphertext() != null
                && entity.getServiceAccountPasswordCiphertext().length > 0;
    }
}
