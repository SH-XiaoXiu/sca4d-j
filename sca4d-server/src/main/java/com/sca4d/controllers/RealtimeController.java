package com.sca4d.controllers;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.auth.annotation.AuthSubject;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.realtime.RealtimeConnectionTargetResolver;
import com.sca4d.common.realtime.RealtimePermissions;
import com.sca4d.common.realtime.RealtimeRoutes;
import com.sca4d.common.realtime.RealtimeSubscription;
import com.sca4d.common.realtime.sse.SseConnectionService;
import com.sca4d.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 低层实时通信接口。
 */
@RestController
@ConditionalOnProperty(prefix = "sca4dj.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping(RealtimeRoutes.HTTP_BASE)
@AuthPrincipal
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeConnectionTargetResolver connectionTargetResolver;
    private final SseConnectionService sseConnectionService;

    /**
     * 查询当前连接可订阅的低层实时目标。
     *
     * @return 低层实时目标列表
     */
    @GetMapping(RealtimeRoutes.HTTP_SUBSCRIPTION)
    @RequirePermission(RealtimePermissions.SUBSCRIPTION_READ)
    public @NotNull ApiResponse<List<RealtimeSubscription>> subscription(@AuthSubject @NotNull Subject subject,
            @AuthPrincipal @NotNull Principal principal) {
        return ApiResponse.ok(connectionTargetResolver.resolve(subject, principal).stream()
                .map(RealtimeSubscription::new)
                .toList());
    }

    /**
     * 建立当前登录上下文的 SSE 连接。
     *
     * @return SSE emitter
     */
    @GetMapping(value = RealtimeRoutes.HTTP_SSE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission(RealtimePermissions.SSE_CONNECT)
    public @NotNull SseEmitter sse(@AuthSubject @NotNull Subject subject, @AuthPrincipal @NotNull Principal principal) {
        return sseConnectionService.connect(subject, principal);
    }
}
