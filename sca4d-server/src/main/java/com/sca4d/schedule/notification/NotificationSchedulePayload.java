package com.sca4d.schedule.notification;

import com.sca4d.notification.NotificationCategory;
import com.sca4d.schedule.ScheduleTaskPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;

/** notification.sse 持久化标题、正文、分类和明确接收人快照。 */
public class NotificationSchedulePayload implements ScheduleTaskPayload {

    private String title;
    private String content;
    private NotificationCategory category;
    private List<NotificationScheduleRecipient> recipients = List.of();

    /** 供 Jackson 按持久化 JSON 恢复字段。 */
    public NotificationSchedulePayload() {
    }

    /** @return 通知标题 */
    public @Nullable String getTitle() {
        return title;
    }

    /** @param title 通知标题 */
    public void setTitle(@NotNull String title) {
        this.title = title;
    }

    /** @return 通知完整正文 */
    public @Nullable String getContent() {
        return content;
    }

    /** @param content 通知完整正文 */
    public void setContent(@NotNull String content) {
        this.content = content;
    }

    /** @return 通知分类 */
    public @Nullable NotificationCategory getCategory() {
        return category;
    }

    /** @param category 通知分类 */
    public void setCategory(@NotNull NotificationCategory category) {
        this.category = category;
    }

    /** @return 按首次出现顺序去重的明确接收人快照 */
    public @NotNull List<NotificationScheduleRecipient> getRecipients() {
        return recipients;
    }

    /** @param recipients 用户选择形成的接收人快照；重复项按完整寻址字段去重 */
    public void setRecipients(@NotNull List<NotificationScheduleRecipient> recipients) {
        this.recipients = List.copyOf(new LinkedHashSet<>(recipients));
    }
}
