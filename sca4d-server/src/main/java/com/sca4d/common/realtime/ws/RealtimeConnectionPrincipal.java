package com.sca4d.common.realtime.ws;

import cn.hutool.core.util.IdUtil;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

import java.security.Principal;

/**
 * WebSocket 连接 Principal。
 */
public class RealtimeConnectionPrincipal implements Principal {

    private final String name;

    /**
     * 创建 WebSocket 连接 Principal。
     *
     * @param name Principal 名称
     */
    public RealtimeConnectionPrincipal(@NotNull String name) {
        this.name = name;
    }

    /**
     * 基于请求认证授权快照创建 WebSocket Principal。
     *
     * @param auth 请求认证授权快照
     * @return WebSocket Principal
     */
    public static @NotNull RealtimeConnectionPrincipal from(@NotNull CurrentRequest auth) {
        Subject subject = auth.getSubject();
        if (subject != null) {
            return new RealtimeConnectionPrincipal("subject:" + subject.getSubjectId());
        }
        return new RealtimeConnectionPrincipal("anonymous:" + IdUtil.fastSimpleUUID());
    }

    @Override
    public @NotNull String getName() {
        return name;
    }
}
