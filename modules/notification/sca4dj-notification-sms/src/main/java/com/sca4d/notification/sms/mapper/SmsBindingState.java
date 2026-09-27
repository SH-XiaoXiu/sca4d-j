package com.sca4d.notification.sms.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存 SMS Provider Binding Mapper 返回的参数映射 JSON 文本。
 */
public final class SmsBindingState {
    private final long bindingId;

    private final long providerId;

    private final @NotNull TreeCode code;

    private final @NotNull String route;

    private final @NotNull String languageTag;

    private final @NotNull String signName;

    private final @NotNull String providerTemplateCode;

    private final @NotNull String parameterMappingJson;

    private final boolean enabled;

    private final @NotNull Version version;

    /**
     * 创建供应商模板绑定数据库状态。
     */
    public SmsBindingState(
            long bindingId,
            long providerId,
            @NotNull TreeCode code,
            @NotNull String route,
            @NotNull String languageTag,
            @NotNull String signName,
            @NotNull String providerTemplateCode,
            @NotNull String parameterMappingJson,
            boolean enabled,
            @NotNull Version version) {
        this.bindingId = bindingId;
        this.providerId = providerId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.route = Objects.requireNonNull(route, "SMS 线路不能为空");
        this.languageTag = Objects.requireNonNull(languageTag, "SMS Locale 不能为空");
        this.signName = Objects.requireNonNull(signName, "短信签名不能为空");
        this.providerTemplateCode = Objects.requireNonNull(providerTemplateCode, "供应商模板编码不能为空");
        this.parameterMappingJson = Objects.requireNonNull(parameterMappingJson, "参数映射 JSON 不能为空");
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "绑定版本不能为空");
    }

    public long bindingId() {
        return bindingId;
    }

    public long providerId() {
        return providerId;
    }

    /**
     * 返回 code 方法提供的结果。
     */
    public @NotNull TreeCode code() {
        return code;
    }

    public @NotNull String route() {
        return route;
    }

    public @NotNull String languageTag() {
        return languageTag;
    }

    public @NotNull String signName() {
        return signName;
    }

    public @NotNull String providerTemplateCode() {
        return providerTemplateCode;
    }

    public @NotNull String parameterMappingJson() {
        return parameterMappingJson;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * 返回 version 方法提供的结果。
     */
    public @NotNull Version version() {
        return version;
    }
}
