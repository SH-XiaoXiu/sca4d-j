package com.sca4d.app.server.notification.view;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.notification.inbox.InboxMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 返回当前 Account 一项站内信渲染快照。 */
@SuppressWarnings("unused")
public class InboxMessageView {
    private final long id;

    private final String notificationType;

    private final String languageTag;

    private final String title;

    private final String text;

    private final String html;

    private final Instant createdAt;

    private final Instant readAt;

    private final long version;

    private InboxMessageView(InboxMessage message) {
        this.id = message.inboxMessageId();
        this.notificationType = message.code().format(SLASH);
        this.languageTag = message.locale().toLanguageTag();
        this.title = message.title();
        this.text = message.text();
        this.html = message.html();
        this.createdAt = message.createdAt();
        this.readAt = message.readAt();
        this.version = message.version().value();
    }

    /** @return 一项站内信的 HTTP 投影 */
    public static @NotNull InboxMessageView from(@NotNull InboxMessage message) {
        return new InboxMessageView(Objects.requireNonNull(message, "站内信不能为空"));
    }

    public long getId() {
        return id;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getHtml() {
        return html;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getReadAt() {
        return readAt;
    }

    public long getVersion() {
        return version;
    }
}
