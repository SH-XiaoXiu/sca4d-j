package com.sca4d.notification.sms;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.notification.NotificationTypeCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理 SMS Provider 实例和统一通知到供应商模板的传输绑定。
 */
public class SmsConfigurationService {

    private static final Logger LOG = LoggerFactory.getLogger(SmsConfigurationService.class);

    private final SmsRepository repository;

    private final NotificationTypeCatalog notificationTypes;

    private final Set<String> transportTypes;

    private final IdGenerator ids;

    /**
     * 创建 SMS 配置管理服务。
     */
    public SmsConfigurationService(
            @NotNull SmsRepository repository,
            @NotNull NotificationTypeCatalog notificationTypes,
            @NotNull List<@NotNull SmsProviderClient> transports,
            @NotNull IdGenerator ids) {
        this.repository = Objects.requireNonNull(repository, "SmsRepository 不能为空");
        this.notificationTypes = Objects.requireNonNull(notificationTypes, "NotificationTypeCatalog 不能为空");
        this.transportTypes = List.copyOf(Objects.requireNonNull(transports, "SMS Transport 不能为空"))
                .stream()
                .map(SmsProviderClient::providerType)
                .collect(Collectors.toUnmodifiableSet());
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
    }

    /**
     * @return 全部未删除 Provider
     */
    @Transactional(readOnly = true)
    public @NotNull List<@NotNull SmsProvider> providers() {
        return repository.providers();
    }

    /**
     * @return 指定 Provider；不存在时抛出稳定错误
     */
    @Transactional(readOnly = true)
    public @NotNull SmsProvider provider(long providerId) {
        return repository.provider(providerId)
                .orElseThrow(() -> new BusinessException(SmsNotificationErrorCode.PROVIDER_NOT_FOUND));
    }

    /**
     * @return 指定 Provider 的全部未删除绑定
     */
    @Transactional(readOnly = true)
    public @NotNull List<@NotNull SmsProviderBinding> bindings(long providerId) {
        provider(providerId);
        return repository.bindings(providerId);
    }

    /**
     * @return 指定绑定；不存在时抛出稳定错误
     */
    @Transactional(readOnly = true)
    public @NotNull SmsProviderBinding binding(long providerId, long bindingId) {
        SmsProviderBinding value = repository.binding(bindingId)
                .orElseThrow(() -> new BusinessException(SmsNotificationErrorCode.BINDING_NOT_FOUND));
        if (value.providerId() != providerId) {
            throw new BusinessException(SmsNotificationErrorCode.BINDING_NOT_FOUND);
        }
        return value;
    }

    /**
     * 创建默认停用 Provider。
     */
    @Transactional
    @CheckReturnValue
    public @NotNull SmsProvider createProvider(
            @NotNull String code,
            @NotNull String type,
            @NotNull String credentialRef,
            int priority) {
        CheckedProvider checked = checkedProvider(code, type, credentialRef, priority);
        long providerId = ids.nextId().value();
        repository.addProvider(providerId, checked.code, checked.type, checked.credentialRef, priority);
        LOG.info(
                "SMS Provider 已创建 | providerId={} code={} type={} enabled=false",
                providerId,
                checked.code,
                checked.type);
        return provider(providerId);
    }

    /**
     * 完整替换停用 Provider。
     */
    @Transactional
    @CheckReturnValue
    public @NotNull SmsProvider replaceProvider(
            long providerId,
            @NotNull String code,
            @NotNull String type,
            @NotNull String credentialRef,
            int priority) {
        if (provider(providerId).enabled()) {
            throw new BusinessException(SmsNotificationErrorCode.PROVIDER_CONFLICT);
        }
        CheckedProvider checked = checkedProvider(code, type, credentialRef, priority);
        repository.replaceProvider(providerId, checked.code, checked.type, checked.credentialRef, priority);
        LOG.info("SMS Provider 已修改 | providerId={} code={} type={}", providerId, checked.code, checked.type);
        return provider(providerId);
    }

    /**
     * 启用或停用 Provider；启用前至少需要一个启用绑定。
     */
    @Transactional
    @CheckReturnValue
    public @NotNull SmsProvider setProviderEnabled(long providerId, boolean enabled) {
        SmsProvider current = provider(providerId);
        if (enabled && bindings(providerId).stream().noneMatch(SmsProviderBinding::enabled)) {
            throw new BusinessException(SmsNotificationErrorCode.PROVIDER_CONFLICT);
        }
        repository.setProviderEnabled(providerId, enabled);
        LOG.info(
                "SMS Provider 状态已修改 | providerId={} previousEnabled={} enabled={}",
                providerId,
                current.enabled(),
                enabled);
        return provider(providerId);
    }

    /**
     * 删除停用且没有绑定的 Provider。
     */
    @Transactional
    public void deleteProvider(long providerId) {
        SmsProvider current = provider(providerId);
        if (current.enabled() || !bindings(providerId).isEmpty()) {
            throw new BusinessException(SmsNotificationErrorCode.PROVIDER_CONFLICT);
        }
        repository.deleteProvider(providerId);
        LOG.info("SMS Provider 已删除 | providerId={}", providerId);
    }

    /**
     * 创建默认停用的 Provider 模板绑定。
     */
    @Transactional
    @CheckReturnValue
    public @NotNull SmsProviderBinding createBinding(
            long providerId,
            @NotNull String notificationCode,
            @NotNull String route,
            @NotNull String languageTag,
            @NotNull String signName,
            @NotNull String providerTemplateCode,
            @NotNull Map<String, String> parameterMapping) {
        provider(providerId);
        CheckedBinding checked = checkedBinding(
                notificationCode,
                route,
                languageTag,
                signName,
                providerTemplateCode,
                parameterMapping);
        long bindingId = ids.nextId().value();
        repository.addBinding(
                bindingId,
                providerId,
                checked.code,
                checked.route,
                checked.locale,
                checked.signName,
                checked.providerTemplateCode,
                checked.parameterMapping);
        LOG.info(
                "SMS Provider 绑定已创建 | providerId={} bindingId={} notificationCode={} route={} locale={}",
                providerId,
                bindingId,
                checked.code,
                checked.route,
                checked.locale.toLanguageTag());
        return binding(providerId, bindingId);
    }

    /**
     * 完整替换停用绑定。
     */
    @Transactional
    @CheckReturnValue
    public @NotNull SmsProviderBinding replaceBinding(
            long providerId,
            long bindingId,
            @NotNull String notificationCode,
            @NotNull String route,
            @NotNull String languageTag,
            @NotNull String signName,
            @NotNull String providerTemplateCode,
            @NotNull Map<String, String> parameterMapping) {
        if (binding(providerId, bindingId).enabled()) {
            throw new BusinessException(SmsNotificationErrorCode.BINDING_CONFLICT);
        }
        CheckedBinding checked = checkedBinding(
                notificationCode,
                route,
                languageTag,
                signName,
                providerTemplateCode,
                parameterMapping);
        repository.replaceBinding(
                bindingId,
                checked.code,
                checked.route,
                checked.locale,
                checked.signName,
                checked.providerTemplateCode,
                checked.parameterMapping);
        LOG.info(
                "SMS Provider 绑定已修改 | providerId={} bindingId={} notificationCode={} route={} locale={}",
                providerId,
                bindingId,
                checked.code,
                checked.route,
                checked.locale.toLanguageTag());
        return binding(providerId, bindingId);
    }

    /**
     * 启用或停用 Provider 模板绑定。
     */
    @Transactional
    @CheckReturnValue
    public @NotNull SmsProviderBinding setBindingEnabled(long providerId, long bindingId, boolean enabled) {
        SmsProviderBinding current = binding(providerId, bindingId);
        if (enabled) {
            checkedBinding(
                    current.code(),
                    current.route(),
                    current.locale(),
                    current.signName(),
                    current.providerTemplateCode(),
                    current.parameterMapping());
        }
        repository.setBindingEnabled(bindingId, enabled);
        LOG.info(
                "SMS Provider 绑定状态已修改 | providerId={} bindingId={} previousEnabled={} enabled={}",
                providerId,
                bindingId,
                current.enabled(),
                enabled);
        return binding(providerId, bindingId);
    }

    /**
     * 删除停用绑定。
     */
    @Transactional
    public void deleteBinding(long providerId, long bindingId) {
        if (binding(providerId, bindingId).enabled()) {
            throw new BusinessException(SmsNotificationErrorCode.BINDING_CONFLICT);
        }
        repository.deleteBinding(bindingId);
        LOG.info("SMS Provider 绑定已删除 | providerId={} bindingId={}", providerId, bindingId);
    }

    /**
     * 校验 Provider 字段并要求类型存在已安装 Transport。
     */
    private @NotNull CheckedProvider checkedProvider(String code, String type, String credentialRef, int priority) {
        try {
            SmsProvider checked = new SmsProvider(
                    1,
                    code,
                    type,
                    credentialRef,
                    priority,
                    false,
                    new com.sca4d.core.api.concurrent.Version(0));
            if (!transportTypes.contains(checked.type())) {
                throw new IllegalArgumentException("SMS Provider 类型没有安装 Transport: " + checked.type());
            }
            return new CheckedProvider(checked.code(), checked.type(), checked.credentialRef());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(SmsNotificationErrorCode.PROVIDER_INVALID, exception);
        }
    }

    /**
     * 校验绑定引用的通知类型、路由、Locale、供应商字段和变量映射。
     */
    private @NotNull CheckedBinding checkedBinding(
            String notificationCode,
            String route,
            String languageTag,
            String signName,
            String providerTemplateCode,
            Map<String, String> parameterMapping) {
        try {
            return checkedBinding(
                    TreeCode.of(notificationCode),
                    new SmsRoute(route),
                    Locale.forLanguageTag(Objects.requireNonNull(languageTag, "Locale 不能为空")),
                    signName,
                    providerTemplateCode,
                    parameterMapping);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(SmsNotificationErrorCode.BINDING_INVALID, exception);
        }
    }

    /**
     * 校验已经解析的绑定编码、Locale、供应商字段和变量映射。
     */
    private @NotNull CheckedBinding checkedBinding(
            @NotNull TreeCode code,
            @NotNull SmsRoute route,
            @NotNull Locale locale,
            String signName,
            String providerTemplateCode,
            Map<String, String> parameterMapping) {
        try {
            TreeCode checkedCode = Objects.requireNonNull(code, "通知编码不能为空");
            var type = notificationTypes.types()
                    .stream()
                    .filter(candidate -> candidate.code().equals(checkedCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("通知类型未安装: " + checkedCode));
            SmsRoute checkedRoute = Objects.requireNonNull(route, "SMS Route 不能为空");
            Locale checkedLocale = Objects.requireNonNull(locale, "Locale 不能为空");
            if (checkedLocale.getLanguage().isBlank()) {
                throw new IllegalArgumentException("Locale 必须包含语言");
            }
            Map<String, String> mapping = new LinkedHashMap<>();
            Map.copyOf(Objects.requireNonNull(parameterMapping, "参数映射不能为空"))
                    .forEach((providerParameter, variable) -> {
                        String checkedProviderParameter = TextUtils.requireText(providerParameter, "Provider 参数名", 128);
                        String checkedVariable = TextUtils.requireText(variable, "通知变量名", 128);
                        if (!type.definition().variables().containsKey(checkedVariable)) {
                            throw new IllegalArgumentException("绑定引用了通知类型未声明的变量: " + checkedVariable);
                        }
                        mapping.put(checkedProviderParameter, checkedVariable);
                    });
            return new CheckedBinding(
                    checkedCode,
                    checkedRoute,
                    checkedLocale,
                    TextUtils.requireText(signName, "短信签名", 128),
                    TextUtils.requireText(providerTemplateCode, "Provider 模板编码", 128),
                    Map.copyOf(mapping));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(SmsNotificationErrorCode.BINDING_INVALID, exception);
        }
    }

    private static final class CheckedProvider {
        private final String code;

        private final String type;

        private final String credentialRef;

        private CheckedProvider(String code, String type, String credentialRef) {
            this.code = code;
            this.type = type;
            this.credentialRef = credentialRef;
        }
    }

    private static final class CheckedBinding {
        private final TreeCode code;

        private final SmsRoute route;

        private final Locale locale;

        private final String signName;

        private final String providerTemplateCode;

        private final Map<String, String> parameterMapping;

        private CheckedBinding(
                TreeCode code,
                SmsRoute route,
                Locale locale,
                String signName,
                String providerTemplateCode,
                Map<String, String> parameterMapping) {
            this.code = code;
            this.route = route;
            this.locale = locale;
            this.signName = signName;
            this.providerTemplateCode = providerTemplateCode;
            this.parameterMapping = parameterMapping;
        }
    }
}
