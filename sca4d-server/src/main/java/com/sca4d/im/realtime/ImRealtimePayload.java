package com.sca4d.im.realtime;

import org.jetbrains.annotations.Nullable;

/**
 * IM 实时事件载荷。
 */
public class ImRealtimePayload {

    private Long conversationId;
    private Long messageId;
    private Long unreadCount;

    public ImRealtimePayload() {
    }

    public ImRealtimePayload(@Nullable Long conversationId, @Nullable Long messageId, @Nullable Long unreadCount) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.unreadCount = unreadCount;
    }

    public @Nullable Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(@Nullable Long conversationId) {
        this.conversationId = conversationId;
    }

    public @Nullable Long getMessageId() {
        return messageId;
    }

    public void setMessageId(@Nullable Long messageId) {
        this.messageId = messageId;
    }

    public @Nullable Long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(@Nullable Long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
