package com.sca4d.notification.inbox.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.notification.inbox.InboxMessage;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** 读写 Account 站内信快照的最小 SQL 形状。 */
public interface InboxMessageMapper {

    /** @return 按游标倒序读取的站内信 */
    @CheckReturnValue
    @NotNull
    List<@NotNull InboxMessage> selectPage(
            @Param("accountId") long accountId,
            @Param("beforeId") @Nullable Long beforeId,
            @Param("limit") int limit);

    /** @return 指定 Account 的未读数量 */
    long countUnread(@Param("accountId") long accountId);

    /** @return 新增成功为 1，重复原始消息为 0 */
    int insert(
            @Param("inboxMessageId") long inboxMessageId,
            @Param("messageId") @NotNull MessageId messageId,
            @Param("accountId") long accountId,
            @Param("notificationCode") @NotNull NotificationCode notificationCode,
            @Param("languageTag") @NotNull String languageTag,
            @Param("title") @NotNull String title,
            @Param("text") @NotNull String text,
            @Param("html") @NotNull String html);

    /** @return 指定站内信首次标记已读的行数 */
    int markRead(
            @Param("accountId") long accountId,
            @Param("inboxMessageId") long inboxMessageId,
            @Param("readAt") @NotNull Instant readAt,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /** @return 当前 Account 本次批量标记已读的行数 */
    int markAllRead(@Param("accountId") long accountId, @Param("readAt") @NotNull Instant readAt);
}
