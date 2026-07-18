package com.sca4d.identity.ldap;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.identity.IdentityClientStateTargets;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderActivationCheck;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.repository.IdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

/** 保存 LDAP 搜索与属性配置，并为用户 bind 保留加密服务账号密码。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultLdapProviderService implements LdapProviderService, IdentityProviderActivationCheck {

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9;-]{0,127}");
    private static final Pattern FILTER_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\d+}");

    private final IdentityProviderRepository identityProviderRepository;
    private final LdapProviderRepository repository;
    private final SecretKeyring keyring;
    private final LdapDirectoryClient directory;

    @Override
    public @NotNull IdentityProviderProtocol protocol() {
        return IdentityProviderProtocol.LDAP;
    }

    @Override
    public boolean canActivate(@NotNull Long providerId) {
        return repository.isConfigured(providerId);
    }

    @Override
    public @NotNull LdapProviderConfiguration configuration(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = requireProvider(scope, providerId);
        requireProtocol(provider);
        LdapProviderConfiguration configuration = repository.findConfiguration(providerId, provider.getVersion());
        if (configuration == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_CONFIGURATION_NOT_FOUND);
        }
        return configuration;
    }

    @Override
    @Transactional
    public @NotNull LdapProviderConfiguration replace(
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
            long version) {
        IdentityProvider provider = requireProvider(scope, providerId);
        requireProtocol(provider);
        if (!provider.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    IdentityProviderErrorCode.PROVIDER_CHANGED,
                    IdentityClientStateTargets.PROVIDERS);
        }
        if (provider.getStatus() != IdentityProviderStatus.DISABLED) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_ACTIVE);
        }

        String checkedUrl = requireUrl(url, transportSecurity);
        String checkedBaseDn = requireDn(baseDn, false);
        String checkedServiceAccountDn = StringUtils.hasText(serviceAccountDn)
                ? requireDn(serviceAccountDn, false)
                : null;
        String checkedUserSearchBase = requireDn(userSearchBase, true);
        String checkedUserSearchFilter = userSearchFilter.trim();
        Matcher placeholders = FILTER_PLACEHOLDER_PATTERN.matcher(checkedUserSearchFilter);
        int placeholderCount = 0;
        boolean usernamePlaceholder = false;
        while (placeholders.find()) {
            placeholderCount++;
            usernamePlaceholder = usernamePlaceholder || "{0}".equals(placeholders.group());
        }
        if (!StringUtils.hasText(checkedUserSearchFilter)
                || placeholderCount != 1
                || !usernamePlaceholder) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        String checkedExternalSubjectAttribute = requireAttribute(externalSubjectAttribute);
        String checkedDisplayNameAttribute = optionalAttribute(displayNameAttribute);
        String checkedEmailAttribute = optionalAttribute(emailAttribute);

        LdapProviderConfiguration current = repository.findConfiguration(providerId, provider.getVersion());
        boolean currentPasswordConfigured = current != null
                && Boolean.TRUE.equals(current.getServiceAccountPasswordConfigured());
        boolean serviceAccountChanged = current == null
                || !Objects.equals(current.getServiceAccountDn(), checkedServiceAccountDn);
        if (checkedServiceAccountDn == null && serviceAccountPassword != null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        if (serviceAccountPassword != null && serviceAccountPassword.isEmpty()) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        if (checkedServiceAccountDn != null
                && serviceAccountPassword == null
                && (serviceAccountChanged || !currentPasswordConfigured)) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_SECRET_REQUIRED);
        }
        EncryptedSecret encrypted = serviceAccountPassword == null
                ? null
                : keyring.encrypt(
                        "identity-provider.ldap." + providerId + ".service-account-password",
                        serviceAccountPassword);
        boolean passwordChanged = encrypted != null
                || (checkedServiceAccountDn == null && currentPasswordConfigured);
        boolean passwordConfigured = checkedServiceAccountDn != null;
        LdapProviderConfiguration saved = new LdapProviderConfiguration(
                providerId,
                checkedUrl,
                checkedBaseDn,
                checkedServiceAccountDn,
                passwordConfigured,
                checkedUserSearchBase,
                checkedUserSearchFilter,
                checkedExternalSubjectAttribute,
                checkedDisplayNameAttribute,
                checkedEmailAttribute,
                transportSecurity,
                version + 1);

        // 父 Provider 版本先使用强 ETag 原子推进；后续协议写入失败会回滚同一事务中的版本变化。
        identityProviderRepository.updateConfigurationVersion(
                providerId, scope, IdentityProviderProtocol.LDAP, version);
        repository.saveConfiguration(saved, encrypted, passwordChanged, current != null);
        log.info(
                "LDAP Provider 配置已写入事务 | providerId={} scopeType={} scopeId={} version={} transportSecurity={} serviceAccountConfigured={} passwordChanged={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                version + 1,
                transportSecurity,
                checkedServiceAccountDn != null,
                passwordChanged);
        return saved;
    }

    @Override
    public void testConnection(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = requireProvider(scope, providerId);
        requireProtocol(provider);
        LdapConnection connection = repository.findConnection(providerId, provider.getVersion());
        if (connection == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_CONFIGURATION_NOT_FOUND);
        }
        directory.testConnection(connection);
        log.info(
                "LDAP Provider 连接测试通过 | providerId={} scopeType={} scopeId={} transportSecurity={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                connection.getTransportSecurity());
    }

    /** @return 与 STARTTLS 或 LDAPS 配置匹配且不包含用户信息、查询和片段的 LDAP URI */
    private @NotNull String requireUrl(
            @NotNull String value, @NotNull LdapTransportSecurity transportSecurity) {
        String checked = value.trim();
        try {
            URI uri = new URI(checked);
            String requiredScheme = transportSecurity == LdapTransportSecurity.LDAPS ? "ldaps" : "ldap";
            if (!requiredScheme.equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
            }
            return checked;
        } catch (URISyntaxException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID, exception);
        }
    }

    /** @return 已由 JNDI 解析的 DN；allowEmpty 只用于相对 base DN 的搜索根 */
    private @NotNull String requireDn(@NotNull String value, boolean allowEmpty) {
        String checked = value.trim();
        if (!allowEmpty && checked.isEmpty()) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        try {
            new LdapName(checked);
            return checked;
        } catch (InvalidNameException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID, exception);
        }
    }

    /** @return 符合 LDAP AttributeDescription 安全集合的必填属性名 */
    private @NotNull String requireAttribute(@Nullable String value) {
        String checked = value == null ? "" : value.trim();
        if (!ATTRIBUTE_PATTERN.matcher(checked).matches()) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        return checked;
    }

    /** @return 已校验的可选 LDAP 属性名；空白输入转换为 null */
    private @Nullable String optionalAttribute(@Nullable String value) {
        return StringUtils.hasText(value) ? requireAttribute(value) : null;
    }

    /** 拒绝通过 LDAP 子资源读取或修改其他协议 Provider。 */
    private void requireProtocol(@NotNull IdentityProvider provider) {
        if (provider.getProtocol() != IdentityProviderProtocol.LDAP) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_MISMATCH);
        }
    }

    /** @return 指定作用域的父 Provider；不存在时返回统一业务错误 */
    private @NotNull IdentityProvider requireProvider(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = identityProviderRepository.findProvider(scope, providerId);
        if (provider == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_NOT_FOUND);
        }
        return provider;
    }
}
