package com.sca4d.app.server.system.request;

import com.sca4d.core.spring.validation.Req;
import com.sca4d.notification.email.EmailTemplateSource;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收一个 Locale 的 Email 主题、纯文本正文和 HTML 正文源码。 */
public class EmailTemplateSourceRequest {

    @Req.NotNull
    @Size(max = 998)
    private @Nullable String subject;

    @Req.NotNull
    @Size(max = 1_000_000)
    private @Nullable String text;

    @Req.NotNull
    @Size(max = 1_000_000)
    private @Nullable String html;

    /** 创建空的请求对象，供 JSON 绑定使用。 */
    public EmailTemplateSourceRequest() {
    }

    private EmailTemplateSourceRequest(@NotNull EmailTemplateSourceRequest source) {
        this.subject = source.subject;
        this.text = source.text;
        this.html = source.html;
    }

    /** 创建请求对象的字段副本，避免保存外部可变请求对象。 */
    @NotNull
    EmailTemplateSourceRequest copy() {
        return new EmailTemplateSourceRequest(this);
    }

    /** @return Email 主题模板 */
    public @NotNull String getSubject() {
        return Objects.requireNonNull(subject, "Email 主题模板不能为空");
    }

    /** @param subject Email 主题模板 */
    public void setSubject(@Nullable String subject) {
        this.subject = subject;
    }

    /** @return Email 纯文本模板 */
    public @NotNull String getText() {
        return Objects.requireNonNull(text, "Email 纯文本模板不能为空");
    }

    /** @param text Email 纯文本模板 */
    public void setText(@Nullable String text) {
        this.text = text;
    }

    /** @return Email HTML 模板 */
    public @NotNull String getHtml() {
        return Objects.requireNonNull(html, "Email HTML 模板不能为空");
    }

    /** @param html Email HTML 模板 */
    public void setHtml(@Nullable String html) {
        this.html = html;
    }

    /** @return Email 模板源码领域对象 */
    public @NotNull EmailTemplateSource source() {
        return new EmailTemplateSource(getSubject(), getText(), getHtml());
    }
}
