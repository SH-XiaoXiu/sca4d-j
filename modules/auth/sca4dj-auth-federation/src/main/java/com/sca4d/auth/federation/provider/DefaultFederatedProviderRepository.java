package com.sca4d.auth.federation.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedProvider;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.auth.federation.FederatedProviderStatus;
import com.sca4d.auth.federation.FederatedSecretCryptography;
import com.sca4d.auth.federation.FederatedUnlinkedIdentityAction;
import com.sca4d.auth.federation.exception.AuthFederationException;
import com.sca4d.auth.federation.provider.mapper.ProviderMapper;
import com.sca4d.auth.federation.provider.mapper.ProviderSettingsState;
import com.sca4d.auth.federation.provider.mapper.ProviderState;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** 使用 `ProviderMapper` 保存联邦 Provider 和协议配置。 */
public class DefaultFederatedProviderRepository implements FederatedProviderRepository {

    private final @NotNull ObjectMapper objectMapper;

    private final @NotNull ProviderMapper mapper;

    private final @NotNull FederatedSecretCryptography secrets;

    private final @NotNull VersionConditions versionConditions;

    /** 创建 Provider Repository。 */
    public DefaultFederatedProviderRepository(
            @NotNull ProviderMapper mapper,
            @Sca4djJson @NotNull Sca4djMapper objectMapper,
            @NotNull FederatedSecretCryptography secrets,
            @NotNull VersionConditions versionConditions) {
        this.mapper = Objects.requireNonNull(mapper, "Provider Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.secrets = Objects.requireNonNull(secrets, "联邦 Secret 加密器不能为空");
        this.versionConditions = Objects.requireNonNull(versionConditions, "版本条件不能为空");
    }

    @Override
    public @NotNull List<FederatedProvider> findAll() {
        return mapper.select(null, null, null).stream().map(this::toProvider).toList();
    }

    @Override
    public @NotNull Optional<FederatedProvider> findByCode(@NotNull String code) {
        return mapper.select(Objects.requireNonNull(code, "Provider 编码不能为空"), null, null)
                .stream()
                .findFirst()
                .map(this::toProvider);
    }

    @Override
    public @NotNull List<FederatedProvider> findLoginProviders() {
        return mapper.select(null, FederatedProviderStatus.ACTIVE, true).stream().map(this::toProvider).toList();
    }

    @Override
    @AffectedRows(target = "新增联邦 Provider")
    public @NotNull FederatedProvider insert(@NotNull FederatedProvider provider) {
        FederatedProvider checked = Objects.requireNonNull(provider, "Provider 不能为空");
        try {
            mapper.insert(
                    checked.code(),
                    checked.protocol(),
                    checked.status(),
                    checked.loginPageVisible(),
                    checked.displayOrder(),
                    checked.unlinkedIdentityAction(),
                    checked.autoLinkVerifiedEmail(),
                    json(checked.i18n()),
                    checked.iconReference());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(FederatedErrorCode.PROVIDER_CONFLICT, exception);
        }
        return findByCode(checked.code()).orElseThrow(() -> new IllegalStateException("新增的联邦 Provider 不存在"));
    }

    @Override
    public @NotNull FederatedProvider replace(
            @NotNull String code,
            boolean loginPageVisible,
            int displayOrder,
            @NotNull FederatedUnlinkedIdentityAction action,
            boolean autoLinkVerifiedEmail,
            @NotNull Map<String, Map<String, String>> i18n,
            @Nullable String iconReference) {
        String checkedCode = Objects.requireNonNull(code, "Provider 编码不能为空");
        FederatedProvider current = findByCode(checkedCode)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.update(
                        checkedCode,
                        Patch.set(loginPageVisible),
                        Patch.set(displayOrder),
                        Patch.set(Objects.requireNonNull(action, "未绑定身份处理方式不能为空")),
                        Patch.set(autoLinkVerifiedEmail),
                        Patch.set(json(Objects.requireNonNull(i18n, "Provider 多语言字段不能为空"))),
                        Patch.<String>choose().setIf(iconReference != null, () -> iconReference).otherwiseClear(),
                        Patch.ignore(),
                        condition.expected()),
                condition.whenZero(),
                "联邦 Provider");
        return findByCode(checkedCode).orElseThrow();
    }

    @Override
    public @NotNull FederatedProvider changeStatus(@NotNull String code, @NotNull FederatedProviderStatus status) {
        String checkedCode = Objects.requireNonNull(code, "Provider 编码不能为空");
        FederatedProvider current = findByCode(checkedCode)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.update(
                        checkedCode,
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.set(Objects.requireNonNull(status, "Provider 状态不能为空")),
                        condition.expected()),
                condition.whenZero(),
                "联邦 Provider");
        return findByCode(checkedCode).orElseThrow();
    }

    @Override
    public void delete(@NotNull String code) {
        String checkedCode = Objects.requireNonNull(code, "Provider 编码不能为空");
        FederatedProvider current = findByCode(checkedCode)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.delete(checkedCode, condition.expected()),
                condition.whenZero(),
                "联邦 Provider");
    }

    @Override
    public @Nullable FederatedProviderConfiguration configuration(@NotNull FederatedProvider provider) {
        return toConfiguration(provider, false);
    }

    @Override
    public @Nullable FederatedProviderConfiguration authorizationConfiguration(@NotNull FederatedProvider provider) {
        return toConfiguration(provider, true);
    }

    @Override
    @Transactional
    public @NotNull FederatedProvider saveConfiguration(
            @NotNull String code,
            @NotNull FederatedProviderConfiguration configuration) {
        String checkedCode = Objects.requireNonNull(code, "Provider 编码不能为空");
        FederatedProvider current = findByCode(checkedCode)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        FederatedProviderConfiguration checked = Objects.requireNonNull(configuration, "协议配置不能为空");
        if (checked.protocol() != current.protocol()) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        }
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.updateConfigurationVersion(current.id(), condition.expected()),
                condition.whenZero(),
                "联邦 Provider");
        FederatedProviderConfiguration old = checked.clientSecret() == null
                ? authorizationConfiguration(current)
                : null;
        EncryptedSecret encrypted = checked.clientSecret() == null
                && old != null
                && old.clientSecret() != null
                        ? secrets.encrypt(current.id(), old.clientSecret())
                        : checked.clientSecret() == null
                                ? null
                                : secrets.encrypt(current.id(), checked.clientSecret());
        AffectedRowCounts.requireSingle(
                mapper.deleteSettings(current.id()),
                ZeroRowPolicy.ALLOW,
                "删除联邦 Provider 协议配置");
        AffectedRowCounts.requireSingle(
                mapper.insertSettings(
                        current.id(),
                        checked.issuer(),
                        checked.clientId(),
                        encrypted == null ? null : encrypted.keyId(),
                        encrypted == null ? null : encrypted.ciphertext(),
                        checked.authorizationEndpoint(),
                        checked.tokenEndpoint(),
                        checked.userInfoEndpoint(),
                        checked.jwksEndpoint(),
                        checked.clientAuthentication(),
                        json(checked.scopes()),
                        json(checked.profileMapping()),
                        json(checked.authorizationParameters())),
                ZeroRowPolicy.ERROR,
                "保存联邦 Provider 协议配置");
        return findByCode(checkedCode)
                .orElseThrow(() -> new IllegalStateException("保存配置后的联邦 Provider 不存在"));
    }

    /** 把数据库配置转换为领域配置；按调用场景决定是否解密 Client Secret。 */
    private @Nullable FederatedProviderConfiguration toConfiguration(
            @NotNull FederatedProvider provider,
            boolean includeSecret) {
        ProviderSettingsState state = mapper.selectSettings(provider.id());
        if (state == null) {
            return null;
        }
        String secret = null;
        byte[] ciphertext = state.secretCiphertext();
        if (includeSecret && ciphertext != null && state.secretKeyId() != null) {
            secret = secrets.decrypt(
                    provider.id(),
                    new EncryptedSecret(state.secretKeyId(), ciphertext));
        }
        return new FederatedProviderConfiguration(
                state.protocol(),
                state.issuer(),
                state.clientId(),
                secret,
                state.authorizationEndpoint(),
                state.tokenEndpoint(),
                state.userInfoEndpoint(),
                state.jwksEndpoint(),
                state.clientAuthentication(),
                readList(state.scopes()),
                readMap(state.profileMapping()),
                readMap(state.authorizationParameters()),
                ciphertext != null);
    }

    /** 把 Provider 查询状态转换为不包含协议 Secret 的领域 Provider。 */
    private @NotNull FederatedProvider toProvider(@NotNull ProviderState state) {
        try {
            return new FederatedProvider(
                    state.id(),
                    state.code(),
                    state.protocol(),
                    state.status(),
                    state.loginPageVisible(),
                    state.displayOrder(),
                    state.unlinkedIdentityAction(),
                    state.autoLinkVerifiedEmail(),
                    readI18n(state.i18n()),
                    state.iconReference(),
                    state.version());
        } catch (RuntimeException exception) {
            throw new AuthFederationException("联邦 Provider 多语言字段损坏", exception);
        }
    }

    /** 把领域集合编码为经过 DataOps 结构校验的 JSON 文本。 */
    private @NotNull String json(Object value) {
        try {
            return Objects.requireNonNull(DataOps.j(objectMapper.writeValueAsString(value)).get(), "JSON 文本不能为空");
        } catch (Exception exception) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID, exception);
        }
    }

    /** 读取 JSON 数组形式的 OAuth2 scope 列表。 */
    private @NotNull List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new AuthFederationException("联邦 Provider 作用域数据损坏", exception);
        }
    }

    /** 读取 JSON 对象形式的字符串映射。 */
    private @NotNull Map<String, String> readMap(String value) {
        return DataOps.j(value)
                .objectValues()
                .entrySet()
                .stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> String.valueOf(entry.getValue())));
    }

    /** 读取 JSON 对象形式的多语言 Provider 文案。 */
    private @NotNull Map<String, Map<String, String>> readI18n(String value) {
        return DataOps.j(value)
                .objectValues()
                .entrySet()
                .stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> {
                                    if (!(entry.getValue() instanceof Map<?, ?> nested)) {
                                        throw new IllegalStateException("联邦 Provider 多语言字段必须是对象");
                                    }
                                    return nested.entrySet()
                                            .stream()
                                            .collect(
                                                    Collectors.toUnmodifiableMap(
                                                            item -> String.valueOf(item.getKey()),
                                                            item -> String.valueOf(item.getValue())));
                                }));
    }
}
