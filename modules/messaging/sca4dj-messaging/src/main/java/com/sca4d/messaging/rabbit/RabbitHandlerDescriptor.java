package com.sca4d.messaging.rabbit;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.HandlingPolicy;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 统一 EventHandler 与普通 MessageHandler 的 RabbitMQ 拓扑字段。 */
final class RabbitHandlerDescriptor {

    private final @NotNull TreeCode code;

    private final @NotNull TreeCode messageCode;

    private final @NotNull HandlingPolicy handlingPolicy;

    private final EventHandler<?> eventHandler;

    private final MessageHandler<?> messageHandler;

    private RabbitHandlerDescriptor(
            @NotNull TreeCode code,
            @NotNull TreeCode messageCode,
            @NotNull HandlingPolicy handlingPolicy,
            EventHandler<?> eventHandler,
            MessageHandler<?> messageHandler) {
        this.code = requireSlash(code, "Handler 编码");
        this.messageCode = requireSlash(messageCode, "消息编码");
        this.handlingPolicy = Objects.requireNonNull(handlingPolicy, "Handler 策略不能为空");
        this.eventHandler = eventHandler;
        this.messageHandler = messageHandler;
    }

    static @NotNull RabbitHandlerDescriptor event(@NotNull EventHandler<?> handler) {
        EventHandler<?> checked = Objects.requireNonNull(handler, "EventHandler 不能为空");
        return new RabbitHandlerDescriptor(
                checked.code(),
                checked.eventType().code(),
                checked.handlingPolicy(),
                checked,
                null);
    }

    static @NotNull RabbitHandlerDescriptor message(@NotNull MessageHandler<?> handler) {
        MessageHandler<?> checked = Objects.requireNonNull(handler, "MessageHandler 不能为空");
        return new RabbitHandlerDescriptor(
                checked.code(),
                checked.codec().code(),
                checked.handlingPolicy(),
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
    HandlingPolicy handlingPolicy() {
        return handlingPolicy;
    }

    boolean event() {
        return eventHandler != null;
    }

    EventHandler<?> eventHandler() {
        return eventHandler;
    }

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
