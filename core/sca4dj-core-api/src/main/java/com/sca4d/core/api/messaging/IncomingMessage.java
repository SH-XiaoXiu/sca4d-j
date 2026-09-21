package com.sca4d.core.api.messaging;

import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 MessageHandler 收到的业务 Message 和 RabbitMQ 投递元数据。 */
public final class IncomingMessage<M extends Message> {

    private final @NotNull MessageId messageId;

    private final @NotNull M message;

    private final @NotNull String sourceApplication;

    private final @NotNull Instant publishedAt;

    private final boolean redelivered;

    private final int attempt;

    public IncomingMessage(
            @NotNull MessageId messageId,
            @NotNull M message,
            @NotNull String sourceApplication,
            @NotNull Instant publishedAt,
            boolean redelivered,
            int attempt) {
        this.messageId = Objects.requireNonNull(messageId, "MessageId 不能为空");
        this.message = Objects.requireNonNull(message, "消息不能为空");
        this.sourceApplication = TextUtils.requireText(sourceApplication, "消息来源应用");
        this.publishedAt = Objects.requireNonNull(publishedAt, "消息发布时间不能为空");
        if (attempt < 1) {
            throw new IllegalArgumentException("消息处理尝试次数必须为正数");
        }
        this.redelivered = redelivered;
        this.attempt = attempt;
    }

    public @NotNull MessageId messageId() {
        return messageId;
    }

    public @NotNull M message() {
        return message;
    }

    public @NotNull String sourceApplication() {
        return sourceApplication;
    }

    public @NotNull Instant publishedAt() {
        return publishedAt;
    }

    public boolean redelivered() {
        return redelivered;
    }

    public int attempt() {
        return attempt;
    }
}
