package com.sca4d.app.server.system.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/** 接收统一通知到一个 SMS Provider 已审核模板的完整绑定。 */
@SuppressWarnings("unused")
public final class SmsBindingRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String notificationCode;

    @NotBlank
    @Size(max = 64)
    private @Nullable String route;

    @NotBlank
    @Size(max = 64)
    private @Nullable String languageTag;

    @NotBlank
    @Size(max = 128)
    private @Nullable String signName;

    @NotBlank
    @Size(max = 128)
    private @Nullable String providerTemplateCode;

    @Valid
    @NotNull
    @Size(max = 100)
    private @Nullable Map<@NotBlank @Size(max = 128) String, @NotBlank @Size(max = 64) String> parameterMapping;

    public String getNotificationCode() {
        return Objects.requireNonNull(notificationCode, "通知编码不能为空");
    }

    public void setNotificationCode(@Nullable String notificationCode) {
        this.notificationCode = notificationCode;
    }

    public String getRoute() {
        return Objects.requireNonNull(route, "SMS 路由不能为空");
    }

    public void setRoute(@Nullable String route) {
        this.route = route;
    }

    public String getLanguageTag() {
        return Objects.requireNonNull(languageTag, "SMS Locale 不能为空");
    }

    public void setLanguageTag(@Nullable String languageTag) {
        this.languageTag = languageTag;
    }

    public String getSignName() {
        return Objects.requireNonNull(signName, "短信签名不能为空");
    }

    public void setSignName(@Nullable String signName) {
        this.signName = signName;
    }

    public String getProviderTemplateCode() {
        return Objects.requireNonNull(providerTemplateCode, "Provider 模板编码不能为空");
    }

    public void setProviderTemplateCode(@Nullable String providerTemplateCode) {
        this.providerTemplateCode = providerTemplateCode;
    }

    public Map<String, String> getParameterMapping() {
        return Map.copyOf(Objects.requireNonNull(parameterMapping, "参数映射不能为空"));
    }

    public void setParameterMapping(@Nullable Map<String, String> parameterMapping) {
        this.parameterMapping = parameterMapping == null ? null : Map.copyOf(parameterMapping);
    }
}
