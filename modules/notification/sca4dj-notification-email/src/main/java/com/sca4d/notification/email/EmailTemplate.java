package com.sca4d.notification.email;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.notification.NotificationCode;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Email 模板聚合；每个 Locale 保存 subject、text 和 html 源码。 */
public final class EmailTemplate {
    private final long templateId;

    private final @NotNull NotificationCode code;

    private final @NotNull Map<@NotNull String, @NotNull EmailTemplateSource> locales;

    private final boolean enabled;

    private final @NotNull Version version;

    public EmailTemplate(
            long templateId,
            @NotNull NotificationCode code,
            @NotNull Map<String, EmailTemplateSource> locales,
            boolean enabled,
            @NotNull Version version) {
        if (templateId <= 0) {
            throw new IllegalArgumentException("Email 模板标识必须为正数");
        }
        this.templateId = templateId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        if (locales.isEmpty()) {
            throw new IllegalArgumentException("Email 模板必须包含 Locale");
        }
        this.locales = Map.copyOf(Objects.requireNonNull(locales, "Email 模板 Locale 不能为空"));
        this.enabled = enabled;
        this.version = Objects.requireNonNull(version, "Email 模板版本不能为空");
    }

    public long templateId() {
        return templateId;
    }

    /** 返回 code 方法提供的结果。 */
    public @NotNull NotificationCode code() {
        return code;
    }

    public @NotNull Map<@NotNull String, @NotNull EmailTemplateSource> locales() {
        return locales;
    }

    /** 返回 source 方法提供的结果。 */
    public @NotNull EmailTemplateSource source(@NotNull Locale requested) {
        Locale checked = Objects.requireNonNull(requested, "Email 模板 Locale 不能为空");
        EmailTemplateSource exact = locales.get(checked.toLanguageTag());
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, EmailTemplateSource> entry : locales.entrySet()) {
            if (Locale.forLanguageTag(entry.getKey()).getLanguage().equals(checked.getLanguage())) {
                return entry.getValue();
            }
        }
        return locales.values().iterator().next();
    }

    public boolean enabled() {
        return enabled;
    }

    /** 返回 version 方法提供的结果。 */
    public @NotNull Version version() {
        return version;
    }
}
