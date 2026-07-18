package com.sca4d.im.view;

import com.sca4d.im.ImConversation;
import com.sca4d.im.ImConversationType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/**
 * IM 会话视图。
 */
@Data
@Accessors(chain = true)
public class ImConversationView {

    private Long conversationId;
    private ImConversationType conversationType;
    private String title;
    private Long unreadCount;
    private Long lastMessageId;
    private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime updatedAt;

    /**
     * 从会话业务对象创建视图。
     *
     * @param conversation 会话业务对象
     * @return 会话视图
     */
    public static @NotNull ImConversationView from(@NotNull ImConversation conversation) {
        return new ImConversationView()
                .setConversationId(conversation.getConversationId())
                .setConversationType(conversation.getConversationType())
                .setTitle(conversation.getTitle())
                .setUnreadCount(conversation.getUnreadCount())
                .setLastMessageId(conversation.getLastMessageId())
                .setLastMessagePreview(conversation.getLastMessagePreview())
                .setLastMessageAt(conversation.getLastMessageAt())
                .setUpdatedAt(conversation.getUpdatedAt());
    }
}
