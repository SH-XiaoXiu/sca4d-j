package com.sca4d.notification.sms.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.notification.NotificationCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 SMS Provider Binding Mapper 返回的参数映射 JSON 文本。 */
public final class SmsBindingState {
    private final long bindingId;

    private final long providerId;

    private final @NotNull NotificationCode code;

    private final @NotNull String route;

    private final @NotNull String languageTag;

    private final @NotNull String signName;

    private final @NotNull String providerTemplateCode;

    private final @NotNull String parameterMappingJson;

    private final boolean enabled;

    private final @NotNull Version version;

    /** 创建供应商模板绑定数据库状态。 */
    public SmsBindingState(
            long bindingId,
            long providerId,
            @NotNull NotificationCode code,
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

    /** 执行 bindingId 方法表示的业务操作。 */
    public long bindingId() {
        return bindingId;
    }

    /** 执行 providerId 方法表示的业务操作。 */
    public long providerId() {
        return providerId;
    }

    /** 返回 code 方法提供的结果。 */
    public @NotNull NotificationCode code() {
        return code;
    }

    /** 执行 route 方法表示的业务操作。 */
    public @NotNull String route() {
        return route;
    }

    /** 执行 languageTag 方法表示的业务操作。 */
    public @NotNull String languageTag() {
        return languageTag;
    }

    /** 执行 signName 方法表示的业务操作。 */
    public @NotNull String signName() {
        return signName;
    }

    /** 执行 providerTemplateCode 方法表示的业务操作。 */
    public @NotNull String providerTemplateCode() {
        return providerTemplateCode;
    }

    /** 执行 parameterMappingJson 方法表示的业务操作。 */
    public @NotNull String parameterMappingJson() {
        return parameterMappingJson;
    }

    /** 执行 enabled 方法表示的业务操作。 */
    public boolean enabled() {
        return enabled;
    }

    /** 返回 version 方法提供的结果。 */
    public @NotNull Version version() {
        return version;
    }
}
