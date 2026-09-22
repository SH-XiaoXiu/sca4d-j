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

    /** 创建 Email 模板响应视图。 */
    private EmailTemplateView(@NotNull EmailTemplate template, @NotNull Set<String> variables) {
        this.templateId = template.templateId();
        this.notificationCode = template.code().value();
        this.locales = template.locales();
        this.variables = Set.copyOf(variables);
        this.enabled = template.enabled();
        this.revision = template.version();
    }

    /** @param template 模板领域对象 @param variables 可用变量 @return 响应视图 */
    @CheckReturnValue
    public static @NotNull EmailTemplateView from(
            @NotNull EmailTemplate template,
            @NotNull Set<String> variables) {
        return new EmailTemplateView(
                Objects.requireNonNull(template, "Email 模板不能为空"),
                Objects.requireNonNull(variables, "Email 模板变量不能为空"));
    }

    /** @return 模板标识 */
    public long getTemplateId() {
        return templateId;
    }

    /** @return 通知编码 */
    public @NotNull String getNotificationCode() {
        return notificationCode;
    }

    /** @return 按 Locale 保存的源码 */
    public @NotNull @Unmodifiable Map<String, EmailTemplateSource> getLocales() {
        return Map.copyOf(locales);
    }

    /** @return 模板变量名 */
    public @NotNull @Unmodifiable Set<String> getVariables() {
        return variables;
    }

    /** @return 是否启用 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return 模板版本数值 */
    public long getVersion() {
        return revision.value();
    }

    /** @return 模板乐观锁版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
