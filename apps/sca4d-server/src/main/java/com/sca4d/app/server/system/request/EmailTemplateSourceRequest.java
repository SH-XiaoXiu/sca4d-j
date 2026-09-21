package com.sca4d.app.server.system.request;

import com.sca4d.core.spring.validation.Req;
import com.sca4d.notification.email.EmailTemplateSource;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收一个 Locale 的 Email 主题、纯文本正文和 HTML 正文源码。 */
public final class EmailTemplateSourceRequest {

    @Req.NotNull
    @Size(max = 998)
    private @Nullable String subject;

    @Req.NotNull
    @Size(max = 1_000_000)
    private @Nullable String text;

    @Req.NotNull
    @Size(max = 1_000_000)
    private @Nullable String html;

    public EmailTemplateSourceRequest() {
    }

    public @NotNull String getSubject() {
        return Objects.requireNonNull(subject, "Email 主题模板不能为空");
    }

    public void setSubject(@Nullable String subject) {
        this.subject = subject;
    }

    public @NotNull String getText() {
        return Objects.requireNonNull(text, "Email 纯文本模板不能为空");
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public @NotNull String getHtml() {
        return Objects.requireNonNull(html, "Email HTML 模板不能为空");
    }

    public void setHtml(@Nullable String html) {
        this.html = html;
    }

    public @NotNull EmailTemplateSource source() {
        return new EmailTemplateSource(getSubject(), getText(), getHtml());
    }
}
