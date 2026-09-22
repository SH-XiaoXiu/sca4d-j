package com.sca4d.notification.inbox;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.NotificationCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** 保存 Account 收件箱中一项不会随模板修改而变化的通知快照。 */
public final class InboxMessage {

    private final long inboxMessageId;

    private final @NotNull MessageId messageId;

    private final long accountId;

    private final @NotNull NotificationCode code;

    private final @NotNull Locale locale;

    private final @NotNull String title;

    private final @NotNull String text;

    private final @NotNull String html;

    private final @NotNull Instant createdAt;

    private final @Nullable Instant readAt;

    private final @NotNull Version version;

    /** 创建从数据库读取的站内信快照。 */
    public InboxMessage(
            long inboxMessageId,
            @NotNull MessageId messageId,
            long accountId,
            @NotNull NotificationCode code,
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

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public long inboxMessageId() {
        return inboxMessageId;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull MessageId messageId() {
        return messageId;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public long accountId() {
        return accountId;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull NotificationCode code() {
        return code;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull Locale locale() {
        return locale;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull String title() {
        return title;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull String text() {
        return text;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull String html() {
        return html;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @Nullable Instant readAt() {
        return readAt;
    }

    /** 执行 Contract 方法表示的业务操作。 */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
