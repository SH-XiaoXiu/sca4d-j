package com.sca4d.notification.inbox;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 保存 Account 收件箱中一项不会随模板修改而变化的通知快照。
 */
@SuppressWarnings("ClassCanBeRecord")
public class InboxMessage {

    private final long inboxMessageId;

    private final @NotNull Id messageId;

    private final long accountId;

    private final @NotNull TreeCode code;

    private final @NotNull Locale locale;

    private final @NotNull String title;

    private final @NotNull String text;

    private final @NotNull String html;

    private final @NotNull Instant createdAt;

    private final @Nullable Instant readAt;

    private final @NotNull Version version;

    /**
     * 创建从数据库读取的站内信快照。
     */
    public InboxMessage(
            long inboxMessageId,
            @NotNull Id messageId,
            long accountId,
            @NotNull TreeCode code,
            @NotNull String languageTag,
            @NotNull String title,
            @NotNull String text,
            @NotNull String html,
            @NotNull Instant createdAt,
            @Nullable Instant readAt,
            @NotNull Version version) {
        if (inboxMessageId <= 0 || accountId <= 0) {
            throw new IllegalArgumentException("站内信和 Account 标识必须为正数");
        }
        this.inboxMessageId = inboxMessageId;
        this.messageId = Objects.requireNonNull(messageId, "原始通知消息标识不能为空");
        this.accountId = accountId;
        this.code = Objects.requireNonNull(code, "通知编码不能为空");
        this.locale = Locale.forLanguageTag(Objects.requireNonNull(languageTag, "站内信 Locale 不能为空"));
        this.title = Objects.requireNonNull(title, "站内信标题不能为空");
        this.text = Objects.requireNonNull(text, "站内信纯文本不能为空");
        this.html = Objects.requireNonNull(html, "站内信 HTML 不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "站内信创建时间不能为空");
        this.readAt = readAt;
        this.version = Objects.requireNonNull(version, "站内信版本不能为空");
    }

    /** @return 站内信数据库标识 */
    @Contract(pure = true)
    public long inboxMessageId() {
        return inboxMessageId;
    }

    /** @return 原始通知消息标识 */
    @Contract(pure = true)
    public @NotNull Id messageId() {
        return messageId;
    }

    /** @return 接收站内信的 Account 标识 */
    @Contract(pure = true)
    public long accountId() {
        return accountId;
    }

    /** @return 通知编码 */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
        return code;
    }

    /** @return 渲染模板使用的 Locale */
    @Contract(pure = true)
    public @NotNull Locale locale() {
        return locale;
    }

    /** @return 站内信标题 */
    @Contract(pure = true)
    public @NotNull String title() {
        return title;
    }

    /** @return 站内信纯文本正文 */
    @Contract(pure = true)
    public @NotNull String text() {
        return text;
    }

    /** @return 站内信安全 HTML 正文 */
    @Contract(pure = true)
    public @NotNull String html() {
        return html;
    }

    /** @return 站内信创建时间 */
    @Contract(pure = true)
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /** @return 首次标记已读时间，未读时为空 */
    @Contract(pure = true)
    public @Nullable Instant readAt() {
        return readAt;
    }

    /** @return 站内信并发版本 */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
