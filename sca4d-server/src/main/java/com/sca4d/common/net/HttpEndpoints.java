package com.sca4d.common.net;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

/** 校验外部 HTTP 端点必须使用 HTTPS，本机测试地址可以使用 HTTP。 */
public final class HttpEndpoints {

    private HttpEndpoints() {
    }

    /**
     * 解析并校验外部 HTTP 端点。
     *
     * @param value 不包含用户信息和片段的绝对 HTTP 端点
     * @return 已确认使用 HTTPS 或本机测试 HTTP 的 URI
     * @throws IllegalArgumentException 地址格式错误或使用不允许的协议时抛出
     */
    public static @NotNull URI parseSecure(@NotNull String value) {
        try {
            URI uri = new URI(value);
            if (!isSecure(uri)) {
                throw new IllegalArgumentException("HTTP 端点必须使用 HTTPS；本机测试地址可以使用 HTTP");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("HTTP 端点格式错误", exception);
        }
    }

    /**
     * 判断 URI 是否可以作为外部 HTTP 端点。
     *
     * @param uri 待检查 URI
     * @return HTTPS，或 host 为 localhost、127.0.0.1、::1 的 HTTP 时返回 true
     */
    public static boolean isSecure(@Nullable URI uri) {
        if (uri == null
                || !uri.isAbsolute()
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        return "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                        || "127.0.0.1".equals(uri.getHost())
                        || "::1".equals(uri.getHost())
                        || "[::1]".equals(uri.getHost()));
    }
}
