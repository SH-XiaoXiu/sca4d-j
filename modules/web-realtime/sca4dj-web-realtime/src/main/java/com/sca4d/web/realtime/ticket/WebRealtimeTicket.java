package com.sca4d.web.realtime.ticket;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回给浏览器的一次性 WebSocket 握手票据和到期时间。 */
public class WebRealtimeTicket {

    private final @NotNull Instant expiresAt;

    private final @NotNull String value;

    public WebRealtimeTicket(@NotNull String value, @NotNull Instant expiresAt) {
        this.value = Objects.requireNonNull(value, "Web Realtime 票据不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Web Realtime 票据到期时间不能为空");
    }

    /** @return 只允许消费一次的随机票据 */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /** @return 票据失效时间 */
    @Contract(pure = true)
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
