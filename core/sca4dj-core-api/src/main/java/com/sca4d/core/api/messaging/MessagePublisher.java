package com.sca4d.core.api.messaging;

import com.sca4d.core.api.event.Event;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 发布业务消息，并由具体 Messaging 实现选择立即发送或使用 Outbox 保证发布。 */
public interface MessagePublisher {

    /**
     * 立即向消息代理发布消息。
     *
     * @param event 待发布的业务事件
     * @return 本次发布分配的 MessageId
     * @throws RuntimeException 消息无法编码、没有匹配路由或代理没有确认发布时抛出
     */
    @CheckReturnValue
    @NotNull
    MessageId publishNow(@NotNull Event<?> event);

    /**
     * 在当前事务成功提交后向消息代理发布消息。
     *
     * @param event 待发布的业务事件
     * @return 本次发布预先分配的 MessageId
     * @throws IllegalStateException 当前线程没有活跃事务时抛出
     */
    @CheckReturnValue
    @NotNull
    MessageId publishAfterCommit(@NotNull Event<?> event);

    /**
     * 在当前事务中写入 Outbox，事务提交后由派发任务持续尝试发布。
     *
     * @param event 待可靠发布的业务事件
     * @return Outbox 记录和最终代理消息共用的 MessageId
     * @throws IllegalStateException 当前线程没有活跃事务时抛出
     */
    @CheckReturnValue
    @NotNull
    MessageId publishReliably(@NotNull Event<?> event);

    /**
     * 在当前事务中写入 Outbox，并且不早于指定时间尝试发布。
     *
     * @param event 待可靠发布的业务事件
     * @param earliestPublishAt 最早允许发布的时间
     * @return Outbox 记录和最终代理消息共用的 MessageId
     * @throws IllegalStateException 当前线程没有活跃事务时抛出
     */
    @CheckReturnValue
    @NotNull
    MessageId publishReliably(@NotNull Event<?> event, @NotNull Instant earliestPublishAt);
}
