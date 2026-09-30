package com.sca4d.notification.email;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Email 模板聚合；每个 Locale 保存 subject、text 和 html 源码。
 */
public class EmailTemplate {

    /**
     * 供 Java 注解参数使用的资源类型文本。
     */
    public static final @NotNull String RESOURCE_TYPE_VALUE = "NOTIFICATION/EMAIL/TEMPLATES";

    /**
     * Email Template 审计使用的稳定资源类型。
     */
    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofSlash("NOTIFICATION/EMAIL/TEMPLATES");

    private final @NotNull Map<@NotNull String, @NotNull EmailTemplateSource> locales;

    private final @NotNull TreeCode code;

    private final @NotNull Version version;

    private final boolean enabled;

    private final long templateId;

    /**
     * @param templateId 模板数据库标识，必须为正数
     * @param code 通知编码
     * @param locales 各 Locale 的模板正文，不能为空
     * @param enabled 是否允许投递
     * @param version 模板版本
     */
    public EmailTemplate(
            long templateId,
            @NotNull TreeCode code,
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

    /** @return 模板数据库标识 */
    public long templateId() {
        return templateId;
    }

    /** @return 邮件模板编码 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** @return 各 Locale 的模板正文 */
    public @NotNull Map<@NotNull String, @NotNull EmailTemplateSource> locales() {
        return locales;
    }

    /** @return 按语言标签选择的模板正文来源 */
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

    /** @return 是否允许投递 */
    public boolean enabled() {
        return enabled;
    }

    /** @return 模板版本 */
    public @NotNull Version version() {
        return version;
    }
}
