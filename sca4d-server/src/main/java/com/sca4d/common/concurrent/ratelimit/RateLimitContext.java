package com.sca4d.common.concurrent.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 限流上下文。
 *
 * <p>HTTP、WebSocket 等入口把自己的调用现场转换成该对象，限流代码根据协议、命令、Principal、客户端地址和请求头生成 key。
 */
public class RateLimitContext {

    private static final String UNKNOWN = "unknown";

    private final String endpoint;
    private final String clientIp;
    private final String userAgent;
    private final String connectionId;

    /**
     * 创建限流上下文。
     *
     * @param endpoint 包含协议和命令名称的接口标识
     * @param clientIp 客户端 IP；缺失时使用未知标记
     * @param userAgent 客户端 User-Agent；缺失时使用未知标记
     * @param connectionId 长连接 ID；短连接调用使用未知标记
     * @throws IllegalArgumentException endpoint 为空白时抛出
     */
    public RateLimitContext(
            @NotNull String endpoint,
            @Nullable String clientIp,
            @Nullable String userAgent,
            @Nullable String connectionId) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException("限流 endpoint 不能为空");
        }
        this.endpoint = endpoint.trim();
        this.clientIp = StringUtils.hasText(clientIp) ? clientIp.trim() : UNKNOWN;
        this.userAgent = StringUtils.hasText(userAgent) ? userAgent.trim() : UNKNOWN;
        this.connectionId = StringUtils.hasText(connectionId) ? connectionId.trim() : UNKNOWN;
    }

    /**
     * 基于 HTTP 请求创建限流上下文。
     *
     * <p>endpoint 优先使用 Spring 最佳匹配路由模板，避免路径变量产生无界桶；客户端地址只使用 Servlet 容器已按可信代理规则解析的 remote address。
     *
     * @param request 当前 HTTP 请求
     * @return 适用于注解限流的 HTTP 请求数据快照
     */
    public static @NotNull RateLimitContext http(@NotNull HttpServletRequest request) {
        Object matchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = matchingPattern instanceof String pattern && StringUtils.hasText(pattern)
                ? pattern
                : request.getRequestURI();
        String endpoint = request.getMethod() + ":" + path;
        return new RateLimitContext(
                endpoint, request.getRemoteAddr(), request.getHeader("User-Agent"), null);
    }

    /**
     * 基于方法调用创建兜底限流上下文。
     *
     * @param endpoint 方法级接口标识
     * @return 方法调用对应的限流上下文
     */
    public static @NotNull RateLimitContext method(@NotNull String endpoint) {
        return new RateLimitContext("METHOD:" + endpoint, null, null, null);
    }

    /**
     * 获取当前接口标识。
     *
     * @return 生成限流 key 使用的接口维度
     */
    public @NotNull String getEndpoint() {
        return endpoint;
    }

    /**
     * 获取当前客户端 IP。
     *
     * @return Servlet 容器未提供连接地址时返回未知标记
     */
    public @NotNull String getClientIp() {
        return clientIp;
    }

    /**
     * 获取当前客户端 User-Agent。
     *
     * @return 未携带 User-Agent 时返回未知标记
     */
    public @NotNull String getUserAgent() {
        return userAgent;
    }

    /**
     * 获取长连接标识。
     *
     * @return 短连接调用时返回未知标记
     */
    public @NotNull String getConnectionId() {
        return connectionId;
    }
}
