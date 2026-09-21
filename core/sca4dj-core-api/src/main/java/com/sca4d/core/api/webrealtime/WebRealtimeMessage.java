package com.sca4d.core.api.webrealtime;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.message.Message;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 请求 Web Realtime 把一个浏览器事件交给当前匹配目标的在线连接。 */
public final class WebRealtimeMessage extends Message {

    public static final @NotNull TreeCode CODE = TreeCode.of("WEB_REALTIME/PUBLISH");

    public static final int PROTOCOL_VERSION = 1;

    private final @NotNull WebRealtimeTarget target;

    private final @NotNull Event<?> browserEvent;

    private final boolean closeAfter;

    private final @NotNull Instant expiresAt;

    public WebRealtimeMessage(
            @NotNull WebRealtimeTarget target,
            @NotNull Event<?> browserEvent,
            boolean closeAfter,
            @NotNull Instant expiresAt) {
        this.target = Objects.requireNonNull(target, "Web Realtime 目标不能为空");
        this.browserEvent = Objects.requireNonNull(browserEvent, "浏览器事件不能为空");
        this.closeAfter = closeAfter;
        this.expiresAt = Objects.requireNonNull(expiresAt, "Web Realtime 消息到期时间不能为空");
        if (!expiresAt.isAfter(browserEvent.occurredAt())) {
            throw new IllegalArgumentException("Web Realtime 消息到期时间必须晚于事件发生时间");
        }
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public @NotNull WebRealtimeTarget target() {
        return target;
    }

    public @NotNull Event<?> browserEvent() {
        return browserEvent;
    }

    public boolean closeAfter() {
        return closeAfter;
    }

    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
