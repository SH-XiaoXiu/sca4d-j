package com.sca4d.app.server.controllers;

import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.spring.web.annotation.CurrentSession;
import com.sca4d.web.realtime.sse.WebRealtimeSseConnector;
import com.sca4d.web.realtime.stomp.WebRealtimeProtocol;
import com.sca4d.web.realtime.ticket.WebRealtimeTicket;
import com.sca4d.web.realtime.ticket.WebRealtimeTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/** 暴露完整示例 App 的 SSE 连接和 WebSocket 一次性票据 HTTP 路由。 */
@RestController
@Tag(name = "Web Realtime")
public class RealtimeController {

    /** 示例 App 对外开放的原生 WebSocket/STOMP 路径。 */
    public static final String WEBSOCKET_PATH = WebRealtimeProtocol.WEBSOCKET_ENDPOINT;

    private final WebRealtimeTicketService tickets;

    private final WebRealtimeSseConnector connections;

    /**
     * 创建 Realtime SSE 入口。
     *
     * @param connections SSE 连接接口
     * @param tickets WebSocket 一次性票据服务
     */
    public RealtimeController(
            @NotNull WebRealtimeSseConnector connections,
            @NotNull WebRealtimeTicketService tickets) {
        this.connections = Objects.requireNonNull(connections, "SSE 连接接口不能为空");
        this.tickets = Objects.requireNonNull(tickets, "WebSocket 票据服务不能为空");
    }

    /**
     * 使用 Bearer Token 建立 SSE 连接。
     *
     * @param session 由在线 Session Token 恢复的当前 Session
     * @param response 用于禁止缓存和反向代理缓冲的 HTTP 响应
     * @return 当前 Session 的 SSE 连接
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireSessionToken
    @Operation(summary = "建立 Realtime SSE 连接")
    public @NotNull SseEmitter connect(
            @CurrentSession @NotNull Session session,
            @NotNull HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return connections.connect(session);
    }

    /**
     * 为当前在线 Session 签发只能在示例 App WebSocket 入口消费一次的短期票据。
     *
     * @param session 由在线 Session Token 恢复的当前 Session
     * @return 随机票据和到期时间
     */
    @PostMapping("/events/ws/tickets")
    @RequireSessionToken
    @Operation(summary = "签发 WebSocket 一次性票据")
    public @NotNull WebRealtimeTicket ticket(
            @CurrentSession @NotNull Session session) {
        return tickets.issue(session, WEBSOCKET_PATH);
    }
}
