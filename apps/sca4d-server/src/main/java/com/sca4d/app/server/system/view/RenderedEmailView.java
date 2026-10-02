package com.sca4d.app.server.system.view;

import com.sca4d.notification.email.RenderedEmail;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 返回完成变量转义和 HTML 清洗后的 Email 模板预览。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class RenderedEmailView {

    private final @Nullable String html;

    private final @NotNull String subject;

    private final @NotNull String text;

    private RenderedEmailView(@NotNull RenderedEmail email) {
        this.subject = email.subject();
        this.text = email.text();
        this.html = email.html();
    }

    /** @return 已渲染 Email 的管理 HTTP 投影 */
    @CheckReturnValue
    public static @NotNull RenderedEmailView from(@NotNull RenderedEmail email) {
        return new RenderedEmailView(Objects.requireNonNull(email, "已渲染 Email 不能为空"));
    }

    /** @return 不含换行的主题 */
    public @NotNull String getSubject() {
        return subject;
    }

    /** @return 纯文本正文 */
    public @NotNull String getText() {
        return text;
    }

    /** @return 可选的已清洗 HTML 正文 */
    public @Nullable String getHtml() {
        return html;
    }
}
