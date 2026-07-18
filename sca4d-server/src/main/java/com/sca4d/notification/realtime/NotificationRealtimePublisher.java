package com.sca4d.notification.realtime;

import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.RealtimeTarget;
import com.sca4d.notification.NotificationRecipient;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 通知实时事件发布器。
 */
@Component
@RequiredArgsConstructor
public class NotificationRealtimePublisher {

    private static final String EVENT_CREATED = "notification.created";
    private static final String EVENT_UNREAD_CHANGED = "notification.unread.changed";

    private final @NotNull ObjectProvider<RealtimePublisher> realtimePublisher;

    /**
     * 发布通知创建事件。
     *
     * @param notificationId 通知主键
     * @param title 通知标题
     * @param content 通知正文
     * @param recipient 接收人
     */
    public void created(
            @NotNull Long notificationId,
            @NotNull String title,
            @NotNull String content,
            @NotNull NotificationRecipient recipient) {
        NotificationRealtimePayload payload = new NotificationRealtimePayload(notificationId, title, content, null);
        publishToRecipient(recipient, EVENT_CREATED, payload);
    }

    /**
     * 发布未读数变化事件。
     *
     * @param recipient 接收人
     * @param unreadCount 未读数
     */
    public void unreadChanged(@NotNull NotificationRecipient recipient, long unreadCount) {
        publishToRecipient(recipient, EVENT_UNREAD_CHANGED, NotificationRealtimePayload.unreadChanged(unreadCount));
    }

    /**
     * 按接收人最具体的目标发布事件。
     *
     * <p>同时包含 Subject 和 Principal 时只投递到 Principal，避免同一连接通过两个订阅目标重复接收，
     * 并保证工作空间通知不会扩散到该 Subject 的其他 Principal。
     *
     * @param recipient 已完成结构校验的接收人
     * @param event 固定实时事件名称
     * @param payload 通知事件载荷
     */
    private void publishToRecipient(
            @NotNull NotificationRecipient recipient, @NotNull String event, @NotNull Object payload) {
        RealtimePublisher publisher = realtimePublisher.getIfAvailable();
        if (publisher == null) {
            return;
        }
        if (recipient.getPrincipalType() != null && recipient.getPrincipalId() != null) {
            publisher.publish(RealtimePublishRequest.sse(
                    RealtimeTarget.principal(recipient.getPrincipalType(), recipient.getPrincipalId()), event, payload));
            return;
        }
        if (recipient.getSubjectId() != null) {
            publisher.publish(RealtimePublishRequest.sse(RealtimeTarget.subject(recipient.getSubjectId()), event, payload));
        }
    }
}
