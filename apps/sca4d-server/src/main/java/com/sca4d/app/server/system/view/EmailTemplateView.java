package com.sca4d.app.server.system.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.notification.template.NotificationTemplate;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;
import java.util.Set;

/** 返回 Email 模板完整多语言字段、变量、状态和版本。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public final class EmailTemplateView {

    private final long templateId;

    private final @NotNull String notificationCode;

    private final @NotNull JsonNode content;

    private final @NotNull Set<@NotNull String> variables;

    private final boolean enabled;

    private final @NotNull Version revision;

    private EmailTemplateView(
            @NotNull NotificationTemplate template,
            @NotNull Set<@NotNull String> variables) {
        this.templateId = template.templateId();
        this.notificationCode = template.code().value();
        this.content = template.content();
        this.variables = Set.copyOf(variables);
        this.enabled = template.enabled();
        this.revision = template.version();
    }

    /** @return Email 模板的管理 HTTP 投影 */
    @CheckReturnValue
    public static @NotNull EmailTemplateView from(
            @NotNull NotificationTemplate template,
            @NotNull Set<@NotNull String> variables) {
        return new EmailTemplateView(
                Objects.requireNonNull(template, "通知模板不能为空"),
                Objects.requireNonNull(variables, "Email 模板变量不能为空"));
    }

    /** @return 模板标识 */
    public long getTemplateId() {
        return templateId;
    }

    /** @return 渠道无关通知编码 */
    public @NotNull String getNotificationCode() {
        return notificationCode;
    }

    /** @return 完整多语言模板字段 */
    public @NotNull JsonNode getContent() {
        return content.deepCopy();
    }

    /** @return 模板引用的不可修改变量集合 */
    public @NotNull @Unmodifiable Set<@NotNull String> getVariables() {
        return variables;
    }

    /** @return 是否参与实际投递 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return Email 模板当前版本 */
    public long getVersion() {
        return revision.value();
    }

    /** @return 生成当前完整 Email 模板表示的版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
