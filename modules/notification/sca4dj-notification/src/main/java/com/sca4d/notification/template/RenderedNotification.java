package com.sca4d.notification.template;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存统一模板为渠道生成的纯文本标题、纯文本正文和安全 HTML 正文。 */
public final class RenderedNotification {

    private final @NotNull String title;

    private final @NotNull String text;

    private final @NotNull String html;

    /** 创建已经完成变量替换与 HTML 转义的渲染结果。 */
    public RenderedNotification(@NotNull String title, @NotNull String text, @NotNull String html) {
        this.title = Objects.requireNonNull(title, "通知标题不能为空");
        this.text = Objects.requireNonNull(text, "通知纯文本正文不能为空");
        this.html = Objects.requireNonNull(html, "通知 HTML 正文不能为空");
    }

    /** @return 不含换行的纯文本标题 */
    @Contract(pure = true)
    public @NotNull String title() {
        return title;
    }

    /** @return 适合 SMS 和文本 Email 的正文 */
    @Contract(pure = true)
    public @NotNull String text() {
        return text;
    }

    /** @return 只由受限 Tiptap schema 生成的安全 HTML */
    @Contract(pure = true)
    public @NotNull String html() {
        return html;
    }
}
