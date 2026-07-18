package com.sca4d.identity.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.net.HttpEndpoints;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 保存标准 OAuth2 协议配置，并提供不依赖具体厂商字段的启用检查。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultOAuth2ProviderService implements OAuth2ProviderService, IdentityProviderActivationCheck {

    private static final String SCOPE_PATTERN = "[A-Za-z0-9._:-]{1,128}";

    private final IdentityProviderRepository identityProviderRepository;
    private final OAuth2ProviderRepository repository;
    private final SecretKeyring keyring;
    private final OAuth2ProfileMapper profileMapper;

    @Override
    public @NotNull IdentityProviderProtocol protocol() {
        return IdentityProviderProtocol.OAUTH2;
    }

    @Override
    public boolean canActivate(@NotNull Long providerId) {
        return repository.isConfigured(providerId);
    }

    @Override
    public @NotNull OAuth2ProviderConfiguration configuration(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = requireProvider(scope, providerId);
        requireProtocol(provider);
        OAuth2ProviderConfiguration configuration = repository.findConfiguration(providerId, provider.getVersion());
        if (configuration == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_CONFIGURATION_NOT_FOUND);
        }
        return configuration;
    }

    @Override
    @Transactional
    public @NotNull OAuth2ProviderConfiguration replace(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull String authorizationUri,
            @NotNull String tokenUri,
            @NotNull String profileUri,
            @Nullable String auxiliaryProfileUri,
            @NotNull String clientId,
            @Nullable String clientSecret,
            @NotNull List<String> scopes,
            @NotNull OAuth2ProfileMapping profileMapping,
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

        String checkedAuthorizationUri = requireEndpoint(authorizationUri);
        String checkedTokenUri = requireEndpoint(tokenUri);
        String checkedProfileUri = requireEndpoint(profileUri);
        String checkedAuxiliaryProfileUri = StringUtils.hasText(auxiliaryProfileUri)
                ? requireEndpoint(auxiliaryProfileUri)
                : null;
        String checkedClientId = clientId.trim();
        if (!StringUtils.hasText(checkedClientId)) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }

        Set<String> checkedScopes = new LinkedHashSet<>();
        for (String scopeValue : scopes) {
            String checked = scopeValue == null ? "" : scopeValue.trim();
            if (!checked.matches(SCOPE_PATTERN)) {
                throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
            }
            checkedScopes.add(checked);
        }
        if (checkedScopes.isEmpty()) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        OAuth2ProfileMapping checkedMapping = new OAuth2ProfileMapping(
                requireJsonPath(profileMapping.getSubjectPath()),
                optionalJsonPath(profileMapping.getDisplayNamePath()),
                optionalJsonPath(profileMapping.getEmailPath()),
                optionalJsonPath(profileMapping.getEmailVerifiedPath()),
                optionalJsonPath(profileMapping.getAvatarPath()));
        OAuth2ProviderConfiguration current = repository.findConfiguration(providerId, provider.getVersion());
        if (clientSecret == null && current == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_SECRET_REQUIRED);
        }
        if (clientSecret != null && clientSecret.isEmpty()) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        EncryptedSecret encrypted = clientSecret == null
                ? null
                : keyring.encrypt("identity-provider.oauth2." + providerId + ".client-secret", clientSecret);
        OAuth2ProviderConfiguration saved = new OAuth2ProviderConfiguration(
                providerId,
                checkedAuthorizationUri,
                checkedTokenUri,
                checkedProfileUri,
                checkedAuxiliaryProfileUri,
                checkedClientId,
                true,
                List.copyOf(checkedScopes),
                checkedMapping,
                version + 1);

        // 父 Provider 版本先使用强 ETag 原子推进；后续协议写入失败会回滚同一事务中的版本变化。
        identityProviderRepository.updateConfigurationVersion(
                providerId, scope, IdentityProviderProtocol.OAUTH2, version);
        repository.saveConfiguration(saved, encrypted, current != null);
        log.info(
                "OAuth2 Provider 配置已写入事务 | providerId={} scopeType={} scopeId={} version={} secretChanged={} auxiliaryProfileConfigured={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                version + 1,
                encrypted != null,
                checkedAuxiliaryProfileUri != null);
        return saved;
    }

    @Override
    public @NotNull OAuth2ProfilePreview preview(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull JsonNode profile,
            @Nullable JsonNode auxiliaryProfile) {
        OAuth2ProviderConfiguration configuration = configuration(scope, providerId);
        return profileMapper.map(profile, auxiliaryProfile, configuration.getProfileMapping());
    }

    /** @return 只允许 HTTPS 或本机测试 HTTP 且不包含用户信息和片段的端点 URI */
    private @NotNull String requireEndpoint(@NotNull String value) {
        String checked = value.trim();
        try {
            HttpEndpoints.parseSecure(checked);
            return checked;
        } catch (IllegalArgumentException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID, exception);
        }
    }

    /** @return 已由 JSONPath 解析器确认可以读取 Profile 的必填路径 */
    private @NotNull String requireJsonPath(@Nullable String value) {
        String checked = value == null ? "" : value.trim();
        if (!checked.startsWith("$")) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        try {
            JsonPath.compile(checked);
            return checked;
        } catch (InvalidPathException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID, exception);
        }
    }

    /** @return 已校验的可选 JSONPath；空白输入转换为 null */
    private @Nullable String optionalJsonPath(@Nullable String value) {
        return StringUtils.hasText(value) ? requireJsonPath(value) : null;
    }

    /** 拒绝通过 OAuth2 子资源读取或修改其他协议 Provider。 */
    private void requireProtocol(@NotNull IdentityProvider provider) {
        if (provider.getProtocol() != IdentityProviderProtocol.OAUTH2) {
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
