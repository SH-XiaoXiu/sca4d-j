package com.sca4d.app.server.system.view;

import com.sca4d.notification.email.RenderedEmail;
import com.sca4d.notification.template.RenderedNotification;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 返回完成变量转义和 HTML 清洗后的 Email 模板预览。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public final class RenderedEmailView {

    private final @NotNull String subject;

    private final @NotNull String text;

    private final @Nullable String html;

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

    /** @return 统一通知模板渲染结果的管理 HTTP 投影 */
    public static @NotNull RenderedEmailView from(@NotNull RenderedNotification notification) {
        RenderedNotification checked = Objects.requireNonNull(notification, "已渲染通知不能为空");
        return new RenderedEmailView(new RenderedEmail(checked.title(), checked.text(), checked.html()));
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
