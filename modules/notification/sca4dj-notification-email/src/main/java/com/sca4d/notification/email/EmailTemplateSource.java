package com.sca4d.notification.email;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Email 模板的主题、纯文本正文和 HTML 正文源码。 */
public final class EmailTemplateSource {
    private final @NotNull String subject;

    private final @NotNull String text;

    private final @NotNull String html;

    public EmailTemplateSource(@NotNull String subject, @NotNull String text, @NotNull String html) {
        this.subject = Objects.requireNonNull(subject, "Email 主题模板不能为空");
        this.text = Objects.requireNonNull(text, "Email 纯文本模板不能为空");
        this.html = Objects.requireNonNull(html, "Email HTML 模板不能为空");
    }

    public @NotNull String subject() {
        return subject;
    }

    public @NotNull String getSubject() {
        return subject;
    }

    public @NotNull String text() {
        return text;
    }

    public @NotNull String getText() {
        return text;
    }

    public @NotNull String html() {
        return html;
    }

    public @NotNull String getHtml() {
        return html;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof EmailTemplateSource source
                        && subject.equals(source.subject)
                        && text.equals(source.text)
                        && html.equals(source.html);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, text, html);
    }
}
