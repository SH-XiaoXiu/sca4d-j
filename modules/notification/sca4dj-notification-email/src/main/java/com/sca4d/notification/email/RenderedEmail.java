package com.sca4d.notification.email;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存已经完成变量转义、主题校验和 HTML 清洗的 Email 内容。 */
public class RenderedEmail {

    /** Email 主题允许的最大字符数。 */
    public static final int MAXIMUM_SUBJECT_LENGTH = 998;

    private final @NotNull String subject;

    private final @NotNull String text;

    private final @Nullable String html;

    /**
     * @param subject 不含回车换行的主题
     * @param text 纯文本正文
     * @param html 可选的已清洗 HTML 正文
     */
    public RenderedEmail(
            @NotNull String subject,
            @NotNull String text,
            @Nullable String html) {
        String checkedSubject = Objects.requireNonNull(subject, "Email 主题不能为空");
        if (checkedSubject.indexOf('\r') >= 0 || checkedSubject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Email 主题不能包含换行");
        }
        if (checkedSubject.length() > MAXIMUM_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("Email 主题不能超过 " + MAXIMUM_SUBJECT_LENGTH + " 个字符");
        }
        this.subject = checkedSubject;
        this.text = Objects.requireNonNull(text, "Email 纯文本正文不能为空");
        this.html = html;
    }

    /** @return 不含回车换行的主题 */
    @Contract(pure = true)
    public @NotNull String subject() {
        return subject;
    }

    /** @return 纯文本正文 */
    @Contract(pure = true)
    public @NotNull String text() {
        return text;
    }

    /** @return 可选的已清洗 HTML 正文 */
    @Contract(pure = true)
    public @Nullable String html() {
        return html;
    }
}
