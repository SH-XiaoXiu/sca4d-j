package com.sca4d.common.realtime.sse;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.realtime.RealtimeConnectionTargetResolver;
import com.sca4d.common.realtime.RealtimeTarget;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;

/** 建立并登记当前登录上下文 SSE 连接的服务。 */
public class SseConnectionService {

    private final SseConnectionRegistry registry;
    private final RealtimeConnectionTargetResolver connectionTargetResolver;
    private final Sca4djRealtimeProperties properties;

    /**
     * 创建 SSE 连接服务。
     *
     * @param registry SSE 本机连接注册表
     * @param connectionTargetResolver 当前连接目标解析器
     * @param properties 实时通信配置
     */
    public SseConnectionService(
            @NotNull SseConnectionRegistry registry,
            @NotNull RealtimeConnectionTargetResolver connectionTargetResolver,
            @NotNull Sca4djRealtimeProperties properties) {
        this.registry = registry;
        this.connectionTargetResolver = connectionTargetResolver;
        this.properties = properties;
    }

    /**
     * 建立当前登录上下文的 SSE 连接。
     *
     * @param subject 登录身份
     * @param principal 当前执行主体
     * @return SSE emitter
     */
    public @NotNull SseEmitter connect(
            @NotNull Subject subject, @NotNull Principal principal) {
        Collection<RealtimeTarget> targets = connectionTargetResolver.resolve(subject, principal);
        SseEmitter emitter = new SseEmitter(properties.getSse().getTimeout().toMillis());
        String connectionId = registry.register(emitter, targets);
        emitter.onCompletion(() -> registry.remove(connectionId));
        emitter.onTimeout(() -> registry.remove(connectionId));
        emitter.onError(error -> registry.remove(connectionId));
        return emitter;
    }
}
