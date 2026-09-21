package com.sca4d.notification.inbox.template;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存站内信模板生成的纯文本标题、纯文本正文和安全 HTML 正文。 */
public final class RenderedInboxMessage {

    private final @NotNull String title;

    private final @NotNull String text;

    private final @NotNull String html;

    /** 创建已经完成变量替换与 HTML 转义的渲染结果。 */
    public RenderedInboxMessage(@NotNull String title, @NotNull String text, @NotNull String html) {
        this.title = Objects.requireNonNull(title, "站内信标题不能为空");
        this.text = Objects.requireNonNull(text, "站内信纯文本正文不能为空");
        this.html = Objects.requireNonNull(html, "站内信 HTML 正文不能为空");
    }

    /** @return 不含换行的纯文本标题 */
    @Contract(pure = true)
    public @NotNull String title() {
        return title;
    }

    /** @return 站内信列表和无 HTML 客户端使用的纯文本正文 */
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
