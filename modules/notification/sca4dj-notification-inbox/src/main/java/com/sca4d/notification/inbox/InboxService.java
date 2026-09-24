package com.sca4d.notification.inbox;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** 查询和修改 Account 站内信，并在提交后提示在线客户端刷新。 */
public class InboxService {

    /** 浏览器收件箱变化事件。 */
    public static final @NotNull EventType<InboxChanged> CHANGED = new EventType<>(
            "NOTIFICATION/INBOX/CHANGED",
            1,
            InboxChanged.class);

    private final InboxRepository repository;

    private final WebRealtimePublisher realtime;

    private final Clock clock;

    /** 创建站内信查询和已读服务。 */
    public InboxService(
            @NotNull InboxRepository repository,
            @NotNull WebRealtimePublisher realtime,
            @NotNull Clock clock) {
        this.repository = Objects.requireNonNull(repository, "InboxRepository 不能为空");
        this.realtime = Objects.requireNonNull(realtime, "WebRealtimePublisher 不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
    }

    /** @return 指定 Account 的站内信页 */
    @Transactional(readOnly = true)
    public @NotNull List<@NotNull InboxMessage> page(long accountId, Long beforeId, int limit) {
        return repository.page(accountId, beforeId, limit);
    }

    /** @return 指定 Account 的未读数量 */
    @Transactional(readOnly = true)
    public long unreadCount(long accountId) {
        return repository.unreadCount(accountId);
    }

    /** 按当前版本把一条站内信标记已读。 */
    @Transactional
    public void markRead(
            long accountId,
            long inboxMessageId,
            @NotNull Version version) {
        boolean changed = repository.markRead(accountId, inboxMessageId, clock.instant(), version);
        if (changed)
            publish(accountId);
    }

    /** 把当前 Account 全部站内信标记已读，并在状态变化后提示在线客户端刷新。 */
    @Transactional
    public void markAllRead(long accountId) {
        int changed = repository.markAllRead(accountId, clock.instant());
        if (changed > 0)
            publish(accountId);
    }

    /** 在事务提交后提示当前 Account 的在线客户端重新读取收件箱。 */
    void publish(long accountId) {
        realtime.publish(
                WebRealtimeTarget.account(accountId),
                new Event<>(CHANGED, Long.toString(accountId), clock.instant(), new InboxChanged(accountId)),
                DispatchPolicy.afterCommit());
    }
}
