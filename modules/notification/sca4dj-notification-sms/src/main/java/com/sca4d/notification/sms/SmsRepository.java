package com.sca4d.notification.sms;

import static com.sca4d.notification.sms.SmsNotificationErrorCode.BINDING_CONFLICT;
import static com.sca4d.notification.sms.SmsNotificationErrorCode.BINDING_NOT_FOUND;
import static com.sca4d.notification.sms.SmsNotificationErrorCode.PROVIDER_CONFLICT;
import static com.sca4d.notification.sms.SmsNotificationErrorCode.PROVIDER_NOT_FOUND;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.notification.sms.mapper.SmsBindingState;
import com.sca4d.notification.sms.mapper.SmsProviderMapper;
import com.sca4d.notification.sms.mapper.SmsProviderState;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 隐藏 SMS Provider 和传输绑定的 Mapper 与 JSON 字段。
 */
public class SmsRepository {
    private final SmsProviderMapper mapper;

    private final ObjectMapper objectMapper;

    private final VersionConditions versionConditions;

    /**
     * 创建 SMS 配置 Repository。
     */
    public SmsRepository(
            @NotNull SmsProviderMapper mapper,
            @NotNull ObjectMapper objectMapper,
            @NotNull VersionConditions versionConditions) {
        this.mapper = Objects.requireNonNull(mapper, "SmsProviderMapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.versionConditions = Objects.requireNonNull(versionConditions, "版本条件不能为空");
    }

    /**
     * @return 全部未删除 SMS Provider
     */
    public @NotNull List<@NotNull SmsProvider> providers() {
        return mapper.selectProviders(null, null, null).stream().map(SmsRepository::provider).toList();
    }

    /**
     * @return 指定 Provider；不存在时为空
     */
    public @NotNull Optional<SmsProvider> provider(long providerId) {
        return mapper.selectProviders(providerId, null, null).stream().findFirst().map(SmsRepository::provider);
    }

    /**
     * @return 指定 Provider 的全部未删除传输绑定
     */
    public @NotNull List<@NotNull SmsProviderBinding> bindings(long providerId) {
        return mapper.selectBindings(null, providerId, null, null, null, null)
                .stream()
                .map(this::binding)
                .toList();
    }

    /**
     * @return 指定绑定；不存在时为空
     */
    public @NotNull Optional<SmsProviderBinding> binding(long bindingId) {
        return mapper.selectBindings(bindingId, null, null, null, null, null)
                .stream()
                .findFirst()
                .map(this::binding);
    }

    /**
     * @return 支持指定线路并按数据库优先级排列的启用 Provider
     */
    public @NotNull List<@NotNull SmsProvider> enabledProviders(@NotNull SmsRoute route) {
        return mapper.selectEnabledProviders(Objects.requireNonNull(route, "SMS 路由不能为空").value())
                .stream()
                .map(SmsRepository::provider)
                .toList();
    }

    /**
     * @return Provider 对当前通知、线路和 Locale 的启用传输绑定
     */
    public @NotNull Optional<SmsProviderBinding> binding(
            long providerId,
            @NotNull TreeCode code,
            @NotNull SmsRoute route,
            @NotNull Locale locale) {
        return mapper.selectBindings(null, providerId, code, route.value(), locale.toLanguageTag(), true)
                .stream()
                .findFirst()
                .map(this::binding);
    }

    /**
     * 新增默认停用的 SMS Provider。
     */
    public void addProvider(
            long providerId,
            @NotNull String code,
            @NotNull String type,
            @NotNull String credentialRef,
            int priority) {
        try {
            mapper.insertProvider(providerId, code, type, credentialRef, priority);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(PROVIDER_CONFLICT, exception);
        }
    }

    /**
     * 按版本条件完整替换停用 Provider。
     */
    public void replaceProvider(
            long providerId,
            @NotNull String code,
            @NotNull String type,
            @NotNull String credentialRef,
            int priority) {
        SmsProvider current = loadProvider(providerId);
        VersionCondition condition = versionConditions.resolve(current.version());
        try {
            AffectedRowCounts.requireSingle(
                    mapper.replaceProvider(
                            providerId,
                            code,
                            type,
                            credentialRef,
                            priority,
                            condition.expected()),
                    condition.whenZero(),
                    "SMS Provider");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(PROVIDER_CONFLICT, exception);
        }
    }

    /**
     * 按版本条件修改 Provider 启用状态。
     */
    public void setProviderEnabled(long providerId, boolean enabled) {
        SmsProvider current = loadProvider(providerId);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.setProviderEnabled(providerId, enabled, condition.expected()),
                condition.whenZero(),
                "SMS Provider");
    }

    /**
     * 按版本条件逻辑删除停用且没有绑定的 Provider。
     */
    public void deleteProvider(long providerId) {
        SmsProvider current = loadProvider(providerId);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.deleteProvider(providerId, condition.expected()),
                condition.whenZero(),
                "SMS Provider");
    }

    /**
     * 新增默认停用的 Provider 模板绑定。
     */
    public void addBinding(
            long bindingId,
            long providerId,
            @NotNull TreeCode code,
            @NotNull SmsRoute route,
            @NotNull Locale locale,
            @NotNull String signName,
            @NotNull String providerTemplateCode,
            @NotNull Map<String, String> parameterMapping) {
        try {
            mapper.insertBinding(
                    bindingId,
                    providerId,
                    code,
                    route.value(),
                    locale.toLanguageTag(),
                    signName,
                    providerTemplateCode,
                    write(parameterMapping));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(BINDING_CONFLICT, exception);
        }
    }

    /**
     * 按版本条件完整替换停用绑定。
     */
    public void replaceBinding(
            long bindingId,
            @NotNull TreeCode code,
            @NotNull SmsRoute route,
            @NotNull Locale locale,
            @NotNull String signName,
            @NotNull String providerTemplateCode,
            @NotNull Map<String, String> parameterMapping) {
        SmsProviderBinding current = loadBinding(bindingId);
        VersionCondition condition = versionConditions.resolve(current.version());
        try {
            AffectedRowCounts.requireSingle(
                    mapper.replaceBinding(
                            bindingId,
                            code,
                            route.value(),
                            locale.toLanguageTag(),
                            signName,
                            providerTemplateCode,
                            write(parameterMapping),
                            condition.expected()),
                    condition.whenZero(),
                    "SMS Provider Binding");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(BINDING_CONFLICT, exception);
        }
    }

    /**
     * 按版本条件修改绑定启用状态。
     */
    public void setBindingEnabled(long bindingId, boolean enabled) {
        SmsProviderBinding current = loadBinding(bindingId);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.setBindingEnabled(bindingId, enabled, condition.expected()),
                condition.whenZero(),
                "SMS Provider Binding");
    }

    /**
     * 按版本条件逻辑删除停用绑定。
     */
    public void deleteBinding(long bindingId) {
        SmsProviderBinding current = loadBinding(bindingId);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.deleteBinding(bindingId, condition.expected()),
                condition.whenZero(),
                "SMS Provider Binding");
    }

    private @NotNull SmsProvider loadProvider(long providerId) {
        return provider(providerId)
                .orElseThrow(() -> new BusinessException(PROVIDER_NOT_FOUND));
    }

    private @NotNull SmsProviderBinding loadBinding(long bindingId) {
        return binding(bindingId)
                .orElseThrow(() -> new BusinessException(BINDING_NOT_FOUND));
    }

    private static SmsProvider provider(SmsProviderState state) {
        return new SmsProvider(
                state.providerId(),
                state.code(),
                state.type(),
                state.credentialRef(),
                state.priority(),
                state.enabled(),
                state.version());
    }

    private SmsProviderBinding binding(SmsBindingState state) {
        try {
            Map<String, String> mapping = objectMapper.readValue(
                    state.parameterMappingJson(),
                    new TypeReference<Map<String, String>>() {
                    });
            return new SmsProviderBinding(
                    state.bindingId(),
                    state.providerId(),
                    state.code(),
                    new SmsRoute(state.route()),
                    state.languageTag(),
                    state.signName(),
                    state.providerTemplateCode(),
                    mapping,
                    state.enabled(),
                    state.version());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的 SMS 参数映射无法解析", exception);
        }
    }

    private @NotNull String write(@NotNull Map<String, String> parameterMapping) {
        try {
            return objectMapper.writeValueAsString(
                    Map.copyOf(Objects.requireNonNull(parameterMapping, "SMS 参数映射不能为空")));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("SMS 参数映射无法编码", exception);
        }
    }
}
