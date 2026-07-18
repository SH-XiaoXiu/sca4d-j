package com.sca4d.identity.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 保存 OIDC 协议配置，并为通用 Provider 启用操作提供配置完整性结果。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultOidcProviderService implements OidcProviderService, IdentityProviderActivationCheck {

    private static final String SCOPE_PATTERN = "[A-Za-z0-9._:-]{1,128}";
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final IdentityProviderRepository identityProviderRepository;
    private final OidcProviderRepository repository;
    private final SecretKeyring keyring;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public @NotNull IdentityProviderProtocol protocol() {
        return IdentityProviderProtocol.OIDC;
    }

    @Override
    public boolean canActivate(@NotNull Long providerId) {
        return repository.isConfigured(providerId);
    }

    @Override
    public @NotNull OidcProviderConfiguration configuration(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = requireProvider(scope, providerId);
        requireProtocol(provider);
        OidcProviderConfiguration configuration = repository.findConfiguration(providerId, provider.getVersion());
        if (configuration == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_CONFIGURATION_NOT_FOUND);
        }
        return configuration;
    }

    @Override
    public @NotNull OidcDiscovery discover(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = requireProvider(scope, providerId);
        requireProtocol(provider);
        OidcProviderConfiguration configuration = repository.findConfiguration(providerId, provider.getVersion());
        if (configuration == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_CONFIGURATION_NOT_FOUND);
        }

        URI discoveryUri = UriComponentsBuilder.fromUriString(configuration.getIssuer())
                .pathSegment(".well-known", "openid-configuration")
                .build()
                .toUri();
        JsonNode metadata;
        long startedAt = System.nanoTime();
        try {
            byte[] responseBody = restClient.get()
                    .uri(discoveryUri)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_FAILED);
                        }
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) {
                            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_INVALID);
                        }
                        return body;
                    });
            metadata = responseBody == null ? null : objectMapper.readTree(responseBody);
        } catch (RestClientException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_FAILED, exception);
        } catch (IOException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_INVALID, exception);
        }
        if (metadata == null || !metadata.isObject()) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_INVALID);
        }

        try {
            String discoveredIssuer = requiredText(metadata, "issuer");
            if (!configuration.getIssuer().equals(discoveredIssuer)) {
                throw new IllegalArgumentException("Discovery issuer 与配置不一致");
            }
            OidcDiscovery discovery = new OidcDiscovery(
                    providerId,
                    discoveredIssuer,
                    requiredEndpoint(metadata, "authorization_endpoint"),
                    requiredEndpoint(metadata, "token_endpoint"),
                    optionalEndpoint(metadata, "userinfo_endpoint"),
                    requiredEndpoint(metadata, "jwks_uri"),
                    stringList(metadata, "scopes_supported"),
                    stringList(metadata, "response_types_supported"),
                    stringList(metadata, "subject_types_supported"),
                    stringList(metadata, "id_token_signing_alg_values_supported"),
                    stringList(metadata, "token_endpoint_auth_methods_supported"),
                    stringList(metadata, "code_challenge_methods_supported"));
            log.debug(
                    "OIDC Discovery 读取完成 | providerId={} scopeType={} scopeId={} durationMs={} userInfoConfigured={} pkceMethods={}",
                    providerId,
                    scope.getScopeType(),
                    scope.getScopeId(),
                    (System.nanoTime() - startedAt) / 1_000_000,
                    discovery.getUserInfoUri() != null,
                    discovery.getCodeChallengeMethodsSupported().size());
            return discovery;
        } catch (IllegalArgumentException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_INVALID, exception);
        }
    }

    @Override
    @Transactional
    public @NotNull OidcProviderConfiguration replace(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull String issuer,
            @NotNull String clientId,
            @Nullable String clientSecret,
            @NotNull List<String> scopes,
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

        String checkedIssuer = issuer.trim();
        String checkedClientId = clientId.trim();
        if (!StringUtils.hasText(checkedIssuer) || !StringUtils.hasText(checkedClientId)) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        try {
            URI uri = HttpEndpoints.parseSecure(checkedIssuer);
            if (uri.getQuery() != null) {
                throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
            }
        } catch (IllegalArgumentException exception) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID, exception);
        }

        Set<String> checkedScopes = new LinkedHashSet<>();
        for (String scopeValue : scopes) {
            String checked = scopeValue == null ? "" : scopeValue.trim();
            if (!checked.matches(SCOPE_PATTERN)) {
                throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
            }
            checkedScopes.add(checked);
        }
        if (!checkedScopes.contains("openid")) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        OidcProviderConfiguration current = repository.findConfiguration(providerId, provider.getVersion());
        if (clientSecret == null && current == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_SECRET_REQUIRED);
        }
        if (clientSecret != null && clientSecret.isEmpty()) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        }
        EncryptedSecret encrypted = clientSecret == null
                ? null
                : keyring.encrypt("identity-provider.oidc." + providerId + ".client-secret", clientSecret);

        // 父 Provider 版本先使用强 ETag 原子推进；后续协议写入失败会回滚同一事务中的版本变化。
        identityProviderRepository.updateConfigurationVersion(
                providerId, scope, IdentityProviderProtocol.OIDC, version);
        repository.saveConfiguration(
                providerId,
                checkedIssuer,
                checkedClientId,
                encrypted,
                String.join(" ", checkedScopes),
                current != null);
        log.info(
                "OIDC Provider 配置已写入事务 | providerId={} scopeType={} scopeId={} version={} secretChanged={}",
                providerId,
                scope.getScopeType(),
                scope.getScopeId(),
                version + 1,
                encrypted != null);
        return new OidcProviderConfiguration(
                providerId,
                checkedIssuer,
                checkedClientId,
                true,
                List.copyOf(checkedScopes),
                version + 1);
    }

    /** 拒绝通过 OIDC 子资源读取或修改其他协议 Provider。 */
    private void requireProtocol(@NotNull IdentityProvider provider) {
        if (provider.getProtocol() != IdentityProviderProtocol.OIDC) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_PROTOCOL_MISMATCH);
        }
    }

    /** @return Discovery 文档中的必填非空字符串 */
    private @NotNull String requiredText(@NotNull JsonNode metadata, @NotNull String field) {
        JsonNode value = metadata.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Discovery 缺少字段: " + field);
        }
        return value.textValue();
    }

    /** @return Discovery 文档中的必填 HTTPS 或本机测试 HTTP 端点 */
    private @NotNull String requiredEndpoint(@NotNull JsonNode metadata, @NotNull String field) {
        return checkedEndpoint(requiredText(metadata, field));
    }

    /** @return Discovery 文档中的可选端点；字段不存在时返回 null */
    private @Nullable String optionalEndpoint(@NotNull JsonNode metadata, @NotNull String field) {
        JsonNode value = metadata.get(field);
        return value == null || value.isNull() ? null : checkedEndpoint(requiredText(metadata, field));
    }

    /** @return 不包含用户信息和片段的 HTTPS 或本机测试 HTTP 端点 */
    private @NotNull String checkedEndpoint(@NotNull String value) {
        try {
            HttpEndpoints.parseSecure(value);
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Discovery 端点无效", exception);
        }
    }

    /** @return Discovery 字符串数组；字段不存在时返回空集合 */
    private @NotNull List<String> stringList(@NotNull JsonNode metadata, @NotNull String field) {
        JsonNode value = metadata.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("Discovery 字段不是数组: " + field);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw new IllegalArgumentException("Discovery 数组包含无效值: " + field);
            }
            values.add(item.textValue());
        }
        return List.copyOf(values);
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
