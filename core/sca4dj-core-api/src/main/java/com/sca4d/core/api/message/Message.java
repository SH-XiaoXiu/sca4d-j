package com.sca4d.core.api.message;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 表示可以交给 MessageBus 并由 RabbitMQ 分发的业务消息。 */
public abstract class Message {

    private final @NotNull List<@NotNull MessageContent> contents;

    /** 创建不携带媒体内容的消息。 */
    protected Message() {
        this(List.of());
    }

    /** @param contents 消息携带的媒体内容 */
    protected Message(@NotNull List<? extends @NotNull MessageContent> contents) {
        this.contents = List.copyOf(Objects.requireNonNull(contents, "消息内容不能为空"));
    }

    /** @return 使用斜杠分层并跨部署保持稳定的消息编码 */
    public abstract @NotNull TreeCode code();

    /** @return 不兼容 wire 结构对应的正数协议版本 */
    public abstract int protocolVersion();

    /** @return 当前消息携带的不可修改媒体内容 */
    public @NotNull @Unmodifiable List<@NotNull MessageContent> contents() {
        return contents;
    }
}
