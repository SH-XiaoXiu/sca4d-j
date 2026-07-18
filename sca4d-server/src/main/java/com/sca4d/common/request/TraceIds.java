package com.sca4d.common.request;

import cn.hutool.core.util.IdUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/** 校验客户端提交的追踪标识，并在标识缺失时生成新的 UUID。 */
public final class TraceIds {

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    /** 禁止创建纯静态追踪标识工具。 */
    private TraceIds() {
    }

    /**
     * 接受安全的外部追踪标识，或生成新的随机标识。
     *
     * <p>外部值只允许字母、数字、点、下划线和短横线，长度不超过 128，避免污染响应头、MDC、消息头和日志结构。
     *
     * @param candidate HTTP、WebSocket 或消息入口收到的外部追踪标识
     * @return 可安全跨协议传播的追踪标识
     */
    public static @NotNull String resolve(@Nullable String candidate) {
        if (candidate != null) {
            String normalized = candidate.trim();
            if (SAFE_TRACE_ID.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return IdUtil.fastSimpleUUID();
    }
}
