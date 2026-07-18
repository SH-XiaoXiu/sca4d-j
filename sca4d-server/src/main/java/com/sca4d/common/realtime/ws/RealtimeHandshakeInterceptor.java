package com.sca4d.common.realtime.ws;

import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Session;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.request.TraceIds;
import com.sca4d.common.web.WebConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 实时连接握手拦截器。
 */
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    /** 握手属性中的 CurrentRequest key。 */
    public static final String CURRENT_REQUEST_ATTRIBUTE = RealtimeHandshakeInterceptor.class.getName() + ".CURRENT_REQUEST";

    /** 握手属性中的客户端 IP key。 */
    public static final String CLIENT_IP_ATTRIBUTE = RealtimeHandshakeInterceptor.class.getName() + ".CLIENT_IP";

    /** 握手属性中的 User-Agent key。 */
    public static final String USER_AGENT_ATTRIBUTE = RealtimeHandshakeInterceptor.class.getName() + ".USER_AGENT";

    private final Authentication authentication;

    /**
     * 创建从共用认证会话恢复 WebSocket 握手身份的拦截器。
     *
     * @param authentication 读取当前 HTTP 握手登录会话的认证实现
     */
    public RealtimeHandshakeInterceptor(@NotNull Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean beforeHandshake(
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            @NotNull Map<String, Object> attributes) {
        HttpServletRequest servletRequest = request instanceof ServletServerHttpRequest servlet
                ? servlet.getServletRequest()
                : null;
        CurrentRequest current = CurrentRequestHolder.currentOrNull();
        if (current == null) {
            current = request(request, servletRequest);
            Session session = authentication.currentSession();
            current.setSession(session);
            current.setSubject(session == null ? null : session.getSubject());
            current.setAccount(session == null ? null : session.getAccount());
        }
        current.requireAccount();
        String userAgent = servletRequest == null
                ? null
                : servletRequest.getHeader("User-Agent");
        attributes.put(CURRENT_REQUEST_ATTRIBUTE, current);
        attributes.put(CLIENT_IP_ATTRIBUTE, current.getClientIp());
        attributes.put(USER_AGENT_ATTRIBUTE, userAgent);
        return true;
    }

    @Override
    public void afterHandshake(
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler,
            Exception exception) {
        // 每个 STOMP 帧会从握手属性恢复独立 CurrentRequest。
    }

    /**
     * 为未经过 HTTP CurrentRequestFilter 的握手创建最小请求数据。
     *
     * <p>客户端地址只读取 Servlet 容器已经解析的 remote address；是否信任代理头由容器可信代理配置决定。
     *
     * @param request Spring WebSocket 握手请求
     * @param servletRequest Servlet 握手请求，非 Servlet 传输时为 null
     * @return 可在后续 STOMP 帧中恢复的握手请求快照
     */
    private @NotNull CurrentRequest request(
            @NotNull ServerHttpRequest request, HttpServletRequest servletRequest) {
        String traceId = TraceIds.resolve(request.getHeaders().getFirst(WebConstants.TRACE_ID_HEADER));
        String clientIp = servletRequest == null ? null : servletRequest.getRemoteAddr();
        return new CurrentRequest(
                traceId,
                "WS:HANDSHAKE",
                request.getURI().getPath(),
                clientIp,
                TimeSupport.now());
    }

}
