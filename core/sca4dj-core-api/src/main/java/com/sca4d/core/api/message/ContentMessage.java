package com.sca4d.core.api.message;

import com.sca4d.core.api.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 保存一种由稳定编码标识、只携带 MessageContent 的普通消息。 */
public final class ContentMessage extends Message {

    private final @NotNull TreeCode code;

    private final int protocolVersion;

    /** 创建只携带媒体内容的普通消息。 */
    public ContentMessage(
            @NotNull TreeCode code,
            int protocolVersion,
            @NotNull List<? extends @NotNull MessageContent> contents) {
        super(contents);
        this.code = Objects.requireNonNull(code, "消息编码不能为空");
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("消息协议版本必须为正数");
        }
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("普通消息至少需要一项内容");
        }
        this.protocolVersion = protocolVersion;
    }

    @Override
    public @NotNull TreeCode code() {
        return code;
    }

    @Override
    public int protocolVersion() {
        return protocolVersion;
    }
}
