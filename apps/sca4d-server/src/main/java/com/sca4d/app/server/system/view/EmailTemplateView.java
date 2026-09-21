package com.sca4d.app.server.system.view;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.notification.email.EmailTemplate;
import com.sca4d.notification.email.EmailTemplateSource;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 返回 Email 模板多语言源码、变量、状态和版本。 */
public final class EmailTemplateView {

    private final long templateId;

    private final @NotNull String notificationCode;

    private final @NotNull Map<String, EmailTemplateSource> locales;

    private final @NotNull Set<String> variables;

    private final boolean enabled;

    private final @NotNull Version revision;

    private EmailTemplateView(@NotNull EmailTemplate template, @NotNull Set<String> variables) {
        this.templateId = template.templateId();
        this.notificationCode = template.code().value();
        this.locales = template.locales();
        this.variables = Set.copyOf(variables);
        this.enabled = template.enabled();
        this.revision = template.version();
    }

    @CheckReturnValue
    public static @NotNull EmailTemplateView from(
            @NotNull EmailTemplate template,
            @NotNull Set<String> variables) {
        return new EmailTemplateView(
                Objects.requireNonNull(template, "Email 模板不能为空"),
                Objects.requireNonNull(variables, "Email 模板变量不能为空"));
    }

    public long getTemplateId() {
        return templateId;
    }

    public @NotNull String getNotificationCode() {
        return notificationCode;
    }

    public @NotNull @Unmodifiable Map<String, EmailTemplateSource> getLocales() {
        return locales;
    }

    public @NotNull @Unmodifiable Set<String> getVariables() {
        return variables;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getVersion() {
        return revision.value();
    }

    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
