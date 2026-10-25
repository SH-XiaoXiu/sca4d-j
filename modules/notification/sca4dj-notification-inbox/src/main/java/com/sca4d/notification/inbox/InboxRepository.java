package com.sca4d.notification.inbox;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.notification.inbox.mapper.InboxMessageMapper;
import com.sca4d.notification.inbox.template.RenderedInboxMessage;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 隐藏站内信 Mapper，对外提供游标分页查询和幂等新增。 */
public class InboxRepository {

    private final @NotNull InboxMessageMapper mapper;

    /** @param mapper 站内信 Mapper */
    public InboxRepository(@NotNull InboxMessageMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "InboxMessageMapper 不能为空");
    }

    /**
     * 按标识倒序读取指定 Account 的站内信。
     *
     * @param beforeId 上一页最后一条的标识，为 null 时从最新一条开始读取；只返回标识小于该值的记录
     * @param limit 本页最多返回的条数，范围为 1 到 100
     * @return 指定 Account 的倒序站内信页，没有数据时为空列表
     */
    @CheckReturnValue
    public @NotNull List<@NotNull InboxMessage> page(long accountId, @Nullable Long beforeId, int limit) {
        if (accountId <= 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("站内信分页参数不合法");
        }
        return List.copyOf(mapper.selectPage(accountId, beforeId, limit));
    }

    /** @return 指定 Account 的未读数量 */
    public long unreadCount(long accountId) {
        if (accountId <= 0)
            throw new IllegalArgumentException("Account 标识必须为正数");
        return mapper.countUnread(accountId);
    }

    /** 按原始 messageId 和 Account 幂等保存渲染快照。 */
    public boolean add(
            long inboxMessageId,
            @NotNull Id messageId,
            long accountId,
            @NotNull Notification notification,
            @NotNull RenderedInboxMessage rendered) {
        return mapper.insert(
                inboxMessageId,
                messageId,
                accountId,
                notification.code(),
                notification.locale().toLanguageTag(),
                rendered.title(),
                rendered.text(),
                rendered.html()) == 1;
    }

    /** 按版本首次标记单条站内信已读。 */
    public boolean markRead(long accountId, long inboxMessageId, @NotNull Instant readAt, @NotNull Version version) {
        return mapper.markRead(accountId, inboxMessageId, readAt, version) == 1;
    }

    /** @return 本次被批量标记已读的数量 */
    public int markAllRead(long accountId, @NotNull Instant readAt) {
        return mapper.markAllRead(accountId, readAt);
    }
}
