package com.sca4d.notification.realtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 通知实时事件载荷。
 */
public class NotificationRealtimePayload {

    private Long notificationId;
    private String title;
    private String content;
    private Long unreadCount;

    /** 创建供消息反序列化使用的空载荷。 */
    public NotificationRealtimePayload() {
    }

    /**
     * 创建通知实时事件载荷。
     *
     * @param notificationId 新建通知事件携带的通知标识
     * @param title 新建通知事件携带的标题
     * @param content 新建通知事件携带的正文
     * @param unreadCount 未读数变化事件携带的最新计数
     */
    public NotificationRealtimePayload(
            @Nullable Long notificationId,
            @Nullable String title,
            @Nullable String content,
            @Nullable Long unreadCount) {
        this.notificationId = notificationId;
        this.title = title;
        this.content = content;
        this.unreadCount = unreadCount;
    }

    /** @return 新建通知的标识 */
    public @Nullable Long getNotificationId() {
        return notificationId;
    }

    /** @param notificationId 新建通知的标识 */
    public void setNotificationId(@Nullable Long notificationId) {
        this.notificationId = notificationId;
    }

    /** @return 新建通知的标题 */
    public @Nullable String getTitle() {
        return title;
    }

    /** @param title 新建通知的标题 */
    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    /** @return 新建通知的正文 */
    public @Nullable String getContent() {
        return content;
    }

    /** @param content 新建通知的正文 */
    public void setContent(@Nullable String content) {
        this.content = content;
    }

    /** @return 当前接收目标的未读通知数 */
    public @Nullable Long getUnreadCount() {
        return unreadCount;
    }

    /** @param unreadCount 当前接收目标的未读通知数 */
    public void setUnreadCount(@Nullable Long unreadCount) {
        this.unreadCount = unreadCount;
    }

    /**
     * 创建未读数变化载荷。
     *
     * @param unreadCount 未读数量
     * @return 通知实时事件载荷
     */
    public static @NotNull NotificationRealtimePayload unreadChanged(long unreadCount) {
        return new NotificationRealtimePayload(null, null, null, unreadCount);
    }
}
