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
     * @param bindingId 绑定数据库标识
     * @param providerId Provider 标识
     * @param code 通知编码
     * @param route SMS 路由
     * @param languageTag Locale 标签
     * @param signName 短信签名
     * @param providerTemplateCode Provider 模板编码
     * @param parameterMappingJson 参数映射 JSON
     * @param enabled 是否允许投递
     * @param version 绑定版本
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

    /** @return 绑定数据库标识 */
    public long bindingId() {
        return bindingId;
    }

    /** @return Provider 标识 */
    public long providerId() {
        return providerId;
    }

    /** @return 通知编码 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** @return SMS 路由 */
    public @NotNull String route() {
        return route;
    }

    /** @return Locale 标签 */
    public @NotNull String languageTag() {
        return languageTag;
    }

    /** @return 短信签名 */
    public @NotNull String signName() {
        return signName;
    }

    /** @return Provider 模板编码 */
    public @NotNull String providerTemplateCode() {
        return providerTemplateCode;
    }

    /** @return 参数映射 JSON */
    public @NotNull String parameterMappingJson() {
        return parameterMappingJson;
    }

    /** @return 是否允许投递 */
    public boolean enabled() {
        return enabled;
    }

    /** @return 绑定版本 */
    public @NotNull Version version() {
        return version;
    }
}
