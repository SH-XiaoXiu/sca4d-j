package com.sca4d.web.realtime.ticket;

import com.sca4d.core.api.authentication.Session;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 为已认证 HTTP 请求签发短期且只能消费一次的 WebSocket 握手票据。 */
public interface WebRealtimeTicketService {

    /**
     * 签发绑定当前 Session 和预期 WebSocket 路径的一次性票据。
     *
     * @param session 当前在线 Session
     * @param endpointPath App 实际开放的 WebSocket 路径
     * @return 随机票据和到期时间
     */
    @CheckReturnValue
    @NotNull
    WebRealtimeTicket issue(@NotNull Session session, @NotNull String endpointPath);
}
