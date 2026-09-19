package com.sca4d.app.server.notification.view;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 返回当前 Account 的站内信游标页和未读数量。 */
@SuppressWarnings("unused")
public final class InboxPageView {
    private final List<InboxMessageView> messages;

    private final long unreadCount;

    public InboxPageView(@NotNull List<InboxMessageView> messages, long unreadCount) {
        this.messages = List.copyOf(messages);
        this.unreadCount = unreadCount;
    }

    public List<InboxMessageView> getMessages() {
        return messages;
    }

    public long getUnreadCount() {
        return unreadCount;
    }
}
