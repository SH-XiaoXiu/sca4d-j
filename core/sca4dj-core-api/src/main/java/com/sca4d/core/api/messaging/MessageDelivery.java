package com.sca4d.core.api.messaging;

import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 RabbitMQ 投递给 `MessageConsumer` 的事件和发布元数据。 */
@SuppressWarnings({"ClassCanBeRecord", "unused"})
public final class MessageDelivery<T> {

    private final @NotNull Id messageId;

    private final @NotNull Event<T> event;

    private final @NotNull String sourceApplication;

    private final @NotNull Instant publishedAt;

    private final boolean redelivered;

    /**
     * 创建已经完成信封校验和 payload 反序列化的消息投递。
     *
     * @param messageId 发布端分配的消息标识
     * @param event 已按 Consumer 的 `EventType` 恢复的事件
     * @param sourceApplication 发布事件的应用技术 ID
     * @param publishedAt 发布端生成消息信封的时间
     * @param redelivered RabbitMQ 是否将当前消息标记为重新投递
     */
    public MessageDelivery(
            @NotNull Id messageId,
            @NotNull Event<T> event,
            @NotNull String sourceApplication,
            @NotNull Instant publishedAt,
            boolean redelivered) {
        this.messageId = Objects.requireNonNull(messageId, "Id 不能为空");
        this.event = Objects.requireNonNull(event, "投递事件不能为空");
        this.sourceApplication = TextUtils.requireText(sourceApplication, "消息来源应用");
        this.publishedAt = Objects.requireNonNull(publishedAt, "消息发布时间不能为空");
        this.redelivered = redelivered;
    }

    /** @return 发布端分配的消息标识，可用于消费幂等检查 */
    @Contract(pure = true)
    public @NotNull Id messageId() {
        return messageId;
    }

    /** @return 已按 Consumer 的 `EventType` 恢复的事件 */
    @Contract(pure = true)
    public @NotNull Event<T> event() {
        return event;
    }

    /** @return 发布事件的应用技术 ID */
    @Contract(pure = true)
    public @NotNull String sourceApplication() {
        return sourceApplication;
    }

    /** @return 发布端生成消息信封的时间 */
    @Contract(pure = true)
    public @NotNull Instant publishedAt() {
        return publishedAt;
    }

    /** @return RabbitMQ 是否将当前消息标记为重新投递 */
    @Contract(pure = true)
    public boolean redelivered() {
        return redelivered;
    }
}
