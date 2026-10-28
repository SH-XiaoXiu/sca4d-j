package com.sca4d.notification.email;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Email 模板一个 Locale 的显示名称、主题、纯文本正文和 HTML 正文源码。 */
public class EmailTemplateSource {
    private final @NotNull String displayName;

    private final @NotNull String html;

    private final @NotNull String subject;

    private final @NotNull String text;

    /**
     * 兼容没有显示名称的旧模板源码。
     *
     * @param subject 邮件主题模板
     * @param text 纯文本正文模板
     * @param html HTML 正文模板
     */
    public EmailTemplateSource(@NotNull String subject, @NotNull String text, @NotNull String html) {
        this("", subject, text, html);
    }

    /**
     * @param displayName 管理界面显示名称
     * @param subject 邮件主题模板
     * @param text 纯文本正文模板
     * @param html HTML 正文模板
     */
    public EmailTemplateSource(
            @NotNull String displayName,
            @NotNull String subject,
            @NotNull String text,
            @NotNull String html) {
        this.displayName = Objects.requireNonNull(displayName, "Email 模板显示名称不能为空");
        this.subject = Objects.requireNonNull(subject, "Email 主题模板不能为空");
        this.text = Objects.requireNonNull(text, "Email 纯文本模板不能为空");
        this.html = Objects.requireNonNull(html, "Email HTML 模板不能为空");
    }

    /** @return 管理界面显示名称 */
    public @NotNull String displayName() {
        return displayName;
    }

    /** @return 管理界面显示名称，供 JavaBean 绑定使用 */
    public @NotNull String getDisplayName() {
        return displayName;
    }

    /** @return 邮件主题模板 */
    public @NotNull String subject() {
        return subject;
    }

    /** @return 邮件主题模板，供 JavaBean 绑定使用 */
    public @NotNull String getSubject() {
        return subject;
    }

    /** @return 纯文本正文模板 */
    public @NotNull String text() {
        return text;
    }

    /** @return 纯文本正文模板，供 JavaBean 绑定使用 */
    public @NotNull String getText() {
        return text;
    }

    /** @return HTML 正文模板 */
    public @NotNull String html() {
        return html;
    }

    /** @return HTML 正文模板，供 JavaBean 绑定使用 */
    public @NotNull String getHtml() {
        return html;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof EmailTemplateSource source
                        && displayName.equals(source.displayName)
                        && subject.equals(source.subject)
                        && text.equals(source.text)
                        && html.equals(source.html);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, subject, text, html);
    }
}
