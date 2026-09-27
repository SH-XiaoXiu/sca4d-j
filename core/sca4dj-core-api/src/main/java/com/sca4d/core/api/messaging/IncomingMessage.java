package com.sca4d.core.api.messaging;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.Name;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 MessageHandler 收到的业务 Message 和 RabbitMQ 投递元数据。 */
@SuppressWarnings("ClassCanBeRecord")
public final class IncomingMessage<M extends Message> {

    private final @NotNull Id messageId;

    private final @NotNull M message;

    private final @NotNull Name sourceApplication;

    private final @NotNull Instant publishedAt;

    private final boolean redelivered;

    private final int attempt;

    /** 创建一条包含 RabbitMQ 投递元数据的业务消息。 */
    public IncomingMessage(
            @NotNull Id messageId,
            @NotNull M message,
            @NotNull Name sourceApplication,
            @NotNull Instant publishedAt,
            boolean redelivered,
            int attempt) {
        this.messageId = Objects.requireNonNull(messageId, "Id 不能为空");
        this.message = Objects.requireNonNull(message, "消息不能为空");
        this.sourceApplication = Objects.requireNonNull(sourceApplication, "消息来源应用不能为空");
        this.publishedAt = Objects.requireNonNull(publishedAt, "消息发布时间不能为空");
        if (attempt < 1) {
            throw new IllegalArgumentException("消息处理尝试次数必须为正数");
        }
        this.redelivered = redelivered;
        this.attempt = attempt;
    }

    /** @return 发布端分配的消息标识 */
    public @NotNull Id messageId() {
        return messageId;
    }

    /** @return 已解码的业务消息 */
    public @NotNull M message() {
        return message;
    }

    /** @return 发布消息的应用标识 */
    public @NotNull Name sourceApplication() {
        return sourceApplication;
    }

    /** @return 消息首次发布时间 */
    public @NotNull Instant publishedAt() {
        return publishedAt;
    }

    /** @return RabbitMQ 是否标记为重新投递 */
    public boolean redelivered() {
        return redelivered;
    }

    /** @return 当前消息的处理尝试序号 */
    public int attempt() {
        return attempt;
    }
}
