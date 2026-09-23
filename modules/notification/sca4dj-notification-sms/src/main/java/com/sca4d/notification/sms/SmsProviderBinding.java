package com.sca4d.notification.sms;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 把统一通知变量绑定到一个 Provider 已审核模板，不保存业务模板正文。
 */
public final class SmsProviderBinding {

    private final long bindingId;

    private final long providerId;

    private final @NotNull TreeCode code;

    private final @NotNull SmsRoute route;

    private final @NotNull Locale locale;

    private final @NotNull String signName;

    private final @NotNull String providerTemplateCode;

    private final @NotNull Map<@NotNull String, @NotNull String> parameterMapping;

    private final boolean enabled;

    private final @NotNull Version version;

    /**
     * 创建一个供应商模板绑定快照。
     */
    public SmsProviderBinding(
            long bindingId,
            long providerId,
            @NotNull TreeCode code,
            @NotNull SmsRoute route,
            @NotNull String languageTag,
            @NotNull String signName,
            @NotNull String providerTemplateCode,
            @NotNull Map<@NotNull String, @NotNull String> parameterMapping,
            boolean enabled,
            @NotNull Version version) {
        if (bindingId <= 0 || providerId <= 0)
            throw new IllegalArgumentException("SMS 绑定标识必须为正数");
        this.bindingId = bindingId;
        this.providerId = providerId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.route = Objects.requireNonNull(route, "SMS 线路不能为空");
        this.locale = Locale.forLanguageTag(Objects.requireNonNull(languageTag, "SMS Locale 不能为空"));
        this.signName = required(signName, "短信签名");
        this.providerTemplateCode = required(providerTemplateCode, "供应商模板编码");
        this.parameterMapping = Map.copyOf(Objects.requireNonNull(parameterMapping, "短信参数映射不能为空"));
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "SMS 绑定版本不能为空");
    }

    /**
     * @return 绑定全局标识
     */
    @Contract(pure = true)
    public long bindingId() {
        return bindingId;
    }

    /**
     * @return 绑定所属 Provider 标识
     */
    @Contract(pure = true)
    public long providerId() {
        return providerId;
    }

    /**
     * @return 统一通知编码
     */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * @return 具体应用声明的 SMS 路由
     */
    @Contract(pure = true)
    public @NotNull SmsRoute route() {
        return route;
    }

    /**
     * @return Provider 模板适用的 Locale
     */
    @Contract(pure = true)
    public @NotNull Locale locale() {
        return locale;
    }

    /**
     * @return Provider 审核通过的短信签名
     */
    @Contract(pure = true)
    public @NotNull String signName() {
        return signName;
    }

    /**
     * @return Provider 审核通过的模板编码
     */
    @Contract(pure = true)
    public @NotNull String providerTemplateCode() {
        return providerTemplateCode;
    }

    /**
     * @return Provider 参数名到统一通知变量名的不可修改映射
     */
    @Contract(pure = true)
    public @NotNull Map<@NotNull String, @NotNull String> parameterMapping() {
        return parameterMapping;
    }

    /**
     * @return 绑定是否参与新投递
     */
    @Contract(pure = true)
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return 绑定当前乐观版本
     */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }

    private static @NotNull String required(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").strip();
        if (checked.isEmpty() || checked.length() > 128)
            throw new IllegalArgumentException(name + "不合法");
        return checked;
    }
}
