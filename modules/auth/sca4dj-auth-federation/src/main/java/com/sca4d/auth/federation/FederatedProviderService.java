package com.sca4d.auth.federation;

import com.sca4d.auth.federation.provider.FederatedProviderRepository;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.LocalizedFieldsSchema;
import com.sca4d.core.api.logging.LogMark;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 编排联邦 Provider 的配置、启停、发现和外部身份访问。 */
public class FederatedProviderService {

    private static final Logger LOG = LoggerFactory.getLogger(FederatedProviderService.class);

    private static final LocalizedFieldsSchema PROVIDER_I18N_SCHEMA = new LocalizedFieldsSchema(
            Set.of("name", "buttonText"),
            Set.of("name", "buttonText"),
            Map.of("name", 128, "buttonText", 256));

    private static final Set<String> ALLOWED_AUTHORIZATION_PARAMETERS = Set.of(
            "prompt",
            "login_hint",
            "ui_locales",
            "max_age",
            "acr_values",
            "hd",
            "access_type",
            "audience",
            "resource");

    private static final Set<String> RESERVED_AUTHORIZATION_PARAMETERS = Set.of(
            "client_id",
            "redirect_uri",
            "response_type",
            "scope",
            "state",
            "nonce",
            "code_challenge",
            "code_challenge_method");

    private final FederatedProviderRepository repository;

    private final LocaleCatalog locales;

    /** 创建 Provider 服务。 */
    public FederatedProviderService(
            @NotNull FederatedProviderRepository repository,
            @NotNull LocaleCatalog locales) {
        this.repository = Objects.requireNonNull(repository, "Provider Repository 不能为空");
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
    }

    /** @return 后台可管理的全部 Provider */
    @Transactional(readOnly = true)
    public @NotNull List<FederatedProvider> findAll() {
        List<FederatedProvider> result = repository.findAll();
        LOG.debug("联邦 Provider 列表读取完成 | count={}", result.size());
        return result;
    }

    /** @return 登录页当前可用的 Provider */
    @Transactional(readOnly = true)
    public @NotNull List<FederatedProvider> findLoginProviders() {
        List<FederatedProvider> result = repository.findLoginProviders();
        LOG.debug("联邦登录 Provider 筛选完成 | count={}", result.size());
        return result;
    }

    /** @return 指定 Provider；不存在时返回空 */
    @Transactional(readOnly = true)
    public @NotNull Optional<FederatedProvider> find(@NotNull String code) {
        return repository.findByCode(code);
    }

    /** 要求 Provider 存在；停用状态不影响后台读取。 */
    @Transactional(readOnly = true)
    public @NotNull FederatedProvider loadByCode(@NotNull String code) {
        return find(code).orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
    }

    /** 要求 Provider 存在且已启用。 */
    @Transactional(readOnly = true)
    public @NotNull FederatedProvider activeByCode(@NotNull String code) {
        FederatedProvider provider = repository.findByCode(code)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        if (provider.status() != FederatedProviderStatus.ACTIVE)
            throw new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE);
        if (repository.configuration(provider) == null)
            throw new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE);
        return provider;
    }

    /** 新增默认停用的 Provider。 */
    @Transactional
    public @NotNull FederatedProvider create(
            @NotNull String code,
            @NotNull FederatedProviderProtocol protocol,
            boolean loginPageVisible,
            int displayOrder,
            @NotNull FederatedUnlinkedIdentityAction action,
            boolean autoLinkVerifiedEmail,
            @NotNull Map<String, Map<String, String>> i18n,
            @Nullable String iconReference) {
        FederatedProvider draft = new FederatedProvider(
                1,
                code,
                protocol,
                FederatedProviderStatus.DISABLED,
                loginPageVisible,
                displayOrder,
                action,
                autoLinkVerifiedEmail,
                validateI18n(i18n),
                iconReference,
                new Version(0));
        FederatedProvider created = repository.insert(draft);
        LOG.atInfo()
                .addMarker(LogMark.AFTER_COMMIT)
                .log(
                        "联邦 Provider 已创建 | providerCode={} protocol={} status={}",
                        created.code(),
                        created.protocol(),
                        created.status());
        return created;
    }

    /**
     * 替换 Provider 的展示和未绑定身份策略。
     *
     * @param code Provider 编码
     * @param loginPageVisible 是否显示在登录页
     * @param displayOrder 登录页排序值
     * @param action 外部身份尚未绑定时的处理方式
     * @param autoLinkVerifiedEmail 是否按已验证邮箱自动关联 Account
     * @param i18n 完整多语言字段
     * @param iconReference 可空图标引用
     * @return 替换后的 Provider
     */
    @Transactional
    public @NotNull FederatedProvider replace(
            @NotNull String code,
            boolean loginPageVisible,
            int displayOrder,
            @NotNull FederatedUnlinkedIdentityAction action,
            boolean autoLinkVerifiedEmail,
            @NotNull Map<String, Map<String, String>> i18n,
            @Nullable String iconReference) {
        String checkedCode = Objects.requireNonNull(code, "Provider 编码不能为空");
        FederatedProvider current = repository.findByCode(checkedCode)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        if (current.status() == FederatedProviderStatus.ACTIVE)
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        FederatedProvider updated = repository.replace(
                checkedCode,
                loginPageVisible,
                displayOrder,
                Objects.requireNonNull(action, "未绑定身份处理方式不能为空"),
                autoLinkVerifiedEmail,
                validateI18n(i18n),
                iconReference);
        LOG.atInfo()
                .addMarker(LogMark.AFTER_COMMIT)
                .log(
                        "联邦 Provider 展示配置已替换 | providerCode={} version={}",
                        updated.code(),
                        updated.version().value());
        return updated;
    }

    /** 保存停用 Provider 的协议配置。 */
    @Transactional
    public @NotNull FederatedProvider replaceConfiguration(
            @NotNull String code,
            @NotNull FederatedProviderConfiguration configuration) {
        FederatedProvider provider = repository.findByCode(code)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        if (provider.status() == FederatedProviderStatus.ACTIVE)
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        validate(configuration);
        FederatedProvider updated = repository.saveConfiguration(provider.code(), configuration);
        boolean clientSecretConfigured = configuration.clientSecret() != null
                || configuration.clientSecretConfigured();
        LOG.atInfo()
                .addMarker(LogMark.AFTER_COMMIT)
                .log(
                        "联邦 Provider 协议配置已替换 | providerCode={} clientSecretConfigured={}",
                        provider.code(),
                        clientSecretConfigured);
        return updated;
    }

    /** 启用前完整校验协议配置；停用可以直接执行。 */
    @Transactional
    public @NotNull FederatedProvider changeStatus(@NotNull String code, @NotNull FederatedProviderStatus status) {
        FederatedProvider provider = repository.findByCode(code)
                .orElseThrow(() -> new BusinessException(FederatedErrorCode.PROVIDER_UNAVAILABLE));
        if (status == FederatedProviderStatus.ACTIVE) {
            FederatedProviderConfiguration configuration = repository.configuration(provider);
            if (configuration == null)
                throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
            validate(configuration);
            if (configuration.clientAuthentication() != FederatedClientAuthentication.NONE
                    && !configuration.clientSecretConfigured()) {
                throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
            }
        }
        FederatedProvider changed = repository.changeStatus(code, status);
        LOG.atInfo()
                .addMarker(LogMark.AFTER_COMMIT)
                .log(
                        "联邦 Provider 状态已修改 | providerCode={} status={}",
                        changed.code(),
                        changed.status());
        return changed;
    }

    /** 删除停用且未绑定外部身份的 Provider。 */
    @Transactional
    public void delete(@NotNull String code) {
        repository.delete(code);
        LOG.atInfo().addMarker(LogMark.AFTER_COMMIT).log("联邦 Provider 已删除 | providerCode={}", code);
    }

    /** @return 不包含 Client Secret 的协议配置摘要 */
    @Transactional(readOnly = true)
    public @Nullable FederatedProviderConfiguration configuration(@NotNull String code) {
        return repository.findByCode(code).map(repository::configuration).orElse(null);
    }

    /** 要求 Provider 协议配置存在，并返回不含 Client Secret 的摘要。 */
    @Transactional(readOnly = true)
    public @NotNull FederatedProviderConfiguration configurationByCode(@NotNull String code) {
        FederatedProvider provider = loadByCode(code);
        FederatedProviderConfiguration configuration = repository.configuration(provider);
        if (configuration == null) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        }
        return configuration;
    }

    /** @return 授权交换使用的协议配置；调用方不得把结果直接返回 HTTP */
    @Transactional(readOnly = true)
    public @Nullable FederatedProviderConfiguration authorizationConfiguration(@NotNull FederatedProvider provider) {
        return repository.authorizationConfiguration(provider);
    }

    /** 校验协议端点、映射和授权参数。 */
    public void validate(@NotNull FederatedProviderConfiguration configuration) {
        FederatedProviderConfiguration checked = Objects.requireNonNull(configuration, "协议配置不能为空");
        requireUrl(checked.issuer(), checked.protocol() == FederatedProviderProtocol.OIDC);
        requireText(checked.clientId());
        if (checked.protocol() == FederatedProviderProtocol.OIDC) {
            if (checked.scopes().stream().noneMatch("openid"::equals))
                throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        } else {
            requireUrl(checked.authorizationEndpoint(), true);
            requireUrl(checked.tokenEndpoint(), true);
            requireUrl(checked.userInfoEndpoint(), true);
            if (checked.profileMapping().get("subject") == null)
                throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
            checked.profileMapping().forEach((key, path) -> {
                if (!Set.of("subject", "displayName", "email", "emailVerified", "avatar").contains(key)
                        || path == null
                        || !path.startsWith("$"))
                    throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
            });
        }
        checked.authorizationParameters().forEach((key, value) -> {
            if (!ALLOWED_AUTHORIZATION_PARAMETERS.contains(key)
                    || RESERVED_AUTHORIZATION_PARAMETERS.contains(key)
                    || value == null
                    || value.isBlank())
                throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        });
    }

    /** 校验登录页多语言名称和按钮文案，并保证应用默认 Locale 可直接展示。 */
    private @NotNull Map<String, Map<String, String>> validateI18n(
            @NotNull Map<String, Map<String, String>> i18n) {
        try {
            return PROVIDER_I18N_SCHEMA
                    .validate(
                            LocalizedFields.fromJson(Objects.requireNonNull(i18n, "Provider 文案不能为空")),
                            locales.defaultLocale())
                    .toJson();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID, exception);
        }
    }

    /** 要求文本存在，避免将空值写入上游请求或数据库。 */
    private static void requireText(String value) {
        if (value == null || value.isBlank())
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
    }

    /** 校验协议端点必须是 HTTPS URL；OAuth2 可省略不使用的 issuer。 */
    private static void requireUrl(String value, boolean required) {
        if ((value == null || value.isBlank()) && !required)
            return;
        if (value == null || value.isBlank())
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID, exception);
        }
    }

}
