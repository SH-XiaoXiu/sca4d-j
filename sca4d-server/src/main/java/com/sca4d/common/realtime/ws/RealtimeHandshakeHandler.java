package com.sca4d.common.realtime.ws;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 实时连接握手处理器。
 */
public class RealtimeHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            @NotNull ServerHttpRequest request,
            @NotNull WebSocketHandler wsHandler,
            @NotNull Map<String, Object> attributes) {
        Object value = attributes.get(RealtimeHandshakeInterceptor.CURRENT_REQUEST_ATTRIBUTE);
        if (value instanceof CurrentRequest currentRequest) {
            return RealtimeConnectionPrincipal.from(currentRequest);
        }
        return super.determineUser(request, wsHandler, attributes);
    }
}
