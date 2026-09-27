package com.sca4d.web.realtime.ticket;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 在 HTTP Upgrade 前校验 Origin，并原子消费一次性票据绑定 Session。 */
@ApiStatus.Internal
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    /** 握手完成后保存 `Session` 的属性键。 */
    public static final String SESSION_ATTRIBUTE = TicketHandshakeInterceptor.class.getName() + ".session";

    /** 握手完成后保存远端 IP 的属性键。 */
    public static final String IP_ATTRIBUTE = TicketHandshakeInterceptor.class.getName() + ".ip";

    private final @NotNull RedisTicketService tickets;

    private final @NotNull List<@NotNull String> allowedOrigins;

    /** 创建使用明确 Origin 列表和 Redis 票据服务的握手拦截器。 */
    public TicketHandshakeInterceptor(
            @NotNull RedisTicketService tickets,
            @NotNull WebRealtimeProperties properties) {
        this.tickets = Objects.requireNonNull(tickets, "Web Realtime 票据服务不能为空");
        this.allowedOrigins = Objects.requireNonNull(properties, "Web Realtime 配置不能为空")
                .getAllowedOrigins();
    }

    /** 校验 Origin 和唯一 ticket 参数，成功时把权威 Session 放入握手属性。 */
    @Override
    public boolean beforeHandshake(
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            @NotNull Map<String, Object> attributes) {
        String origin = request.getHeaders().getOrigin();
        if (origin == null || !allowedOrigins.contains(origin)) {
            return false;
        }
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams();
        List<String> ticketValues = query.get("ticket");
        if (ticketValues == null || ticketValues.size() != 1) {
            return false;
        }
        Session session = tickets.consume(ticketValues.getFirst(), request.getURI().getPath())
                .orElse(null);
        if (session == null) {
            return false;
        }
        attributes.put(SESSION_ATTRIBUTE, session);
        InetSocketAddress remote = request.getRemoteAddress();
        attributes.put(IP_ATTRIBUTE, remoteAddress(remote));
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        return true;
    }

    /** 握手完成后没有额外状态需要保留。 */
    @Override
    public void afterHandshake(
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            @Nullable Exception exception) {
    }

    /** 返回限流使用的远端 IP；地址未解析时保留主机文本。 */
    private static @NotNull String remoteAddress(InetSocketAddress remote) {
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() == null
                ? remote.getHostString()
                : remote.getAddress().getHostAddress();
    }
}
