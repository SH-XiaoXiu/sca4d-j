package com.sca4d.messaging.rabbit;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存 EventHandler 或 MessageHandler 对应的 RabbitMQ 队列、路由和重试规则字段。 */
class RabbitHandlerDescriptor {

    /** 事件 Handler；消息 Handler 描述符中为空。 */
    private final @Nullable EventHandler<?> eventHandler;

    /** 消息 Handler；事件 Handler 描述符中为空。 */
    private final @Nullable MessageHandler<?> messageHandler;

    private final @NotNull RetryRule retryRule;

    private final @NotNull TreeCode code;

    private final @NotNull TreeCode messageCode;

    private RabbitHandlerDescriptor(
            @NotNull TreeCode code,
            @NotNull TreeCode messageCode,
            @NotNull RetryRule retryRule,
            @Nullable EventHandler<?> eventHandler,
            @Nullable MessageHandler<?> messageHandler) {
        this.code = requireSlash(code, "Handler 编码");
        this.messageCode = requireSlash(messageCode, "消息编码");
        this.retryRule = Objects.requireNonNull(retryRule, "Handler 策略不能为空");
        this.eventHandler = eventHandler;
        this.messageHandler = messageHandler;
    }

    static @NotNull RabbitHandlerDescriptor event(@NotNull EventHandler<?> handler) {
        EventHandler<?> checked = Objects.requireNonNull(handler, "EventHandler 不能为空");
        return new RabbitHandlerDescriptor(
                checked.code(),
                checked.eventType().code(),
                checked.retryRule(),
                checked,
                null);
    }

    static @NotNull RabbitHandlerDescriptor message(@NotNull MessageHandler<?> handler) {
        MessageHandler<?> checked = Objects.requireNonNull(handler, "MessageHandler 不能为空");
        return new RabbitHandlerDescriptor(
                checked.code(),
                checked.codec().code(),
                checked.retryRule(),
                null,
                checked);
    }

    @NotNull
    TreeCode code() {
        return code;
    }

    @NotNull
    TreeCode messageCode() {
        return messageCode;
    }

    @NotNull
    RetryRule retryRule() {
        return retryRule;
    }

    boolean event() {
        return eventHandler != null;
    }

    @Nullable
    EventHandler<?> eventHandler() {
        return eventHandler;
    }

    @Nullable
    MessageHandler<?> messageHandler() {
        return messageHandler;
    }

    private static @NotNull TreeCode requireSlash(@NotNull TreeCode code, @NotNull String name) {
        TreeCode checked = Objects.requireNonNull(code, name + "不能为空");
        if (checked.hasNode(TreeCode.ofSegment("*")) || checked.format(SLASH).length() > 128) {
            throw new IllegalArgumentException(name + "必须使用斜杠分层且不能超过 128 个字符");
        }
        return checked;
    }
}
