package com.sca4d.core.spring.gateway;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** 验证 Gateway 控制 Secret，采用可信转发信息并对后续处理隐藏控制 Header。 */
public class TrustedGatewayRequestFilter extends OncePerRequestFilter {

    private final byte @NotNull [] expectedSecret;

    /**
     * 创建可信 Gateway 请求过滤器。
     *
     * @param expectedSecret Server 注册和 Gateway 转发共同使用的 Secret
     * @throws IllegalArgumentException Secret 为空或只有空白字符时抛出
     */
    public TrustedGatewayRequestFilter(@Nullable String expectedSecret) {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            throw new IllegalArgumentException("sca4dj.gateway.registration.secret 不能为空");
        }
        this.expectedSecret = expectedSecret.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        if (matches(request.getHeader(GatewayHttpHeaders.SECRET))) {
            String clientIp = firstForwardedAddress(request.getHeader(GatewayHttpHeaders.CLIENT_IP));
            String externalPath = externalPath(
                    request.getHeader(GatewayHttpHeaders.EXTERNAL_PREFIX),
                    request.getRequestURI());
            if (clientIp != null) {
                request.setAttribute(TrustedGatewayRequest.CLIENT_IP_ATTRIBUTE, clientIp);
            }
            if (externalPath != null) {
                request.setAttribute(TrustedGatewayRequest.EXTERNAL_PATH_ATTRIBUTE, externalPath);
            }
        }
        filterChain.doFilter(new HiddenGatewayHeadersRequest(request), response);
    }

    /** 使用固定时间字节比较验证 Gateway Secret。 */
    private boolean matches(@Nullable String candidate) {
        byte[] actual = candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedSecret, actual);
    }

    /** 读取最靠近客户端的转发地址，并拒绝控制字符和异常长度。 */
    private static @Nullable String firstForwardedAddress(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String first = value.split(",", 2)[0].strip();
        if (first.isEmpty() || first.length() > 255 || first.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return first;
    }

    /** 把 Gateway 移除的公开前缀加回应用内部路径，供审计和签名使用。 */
    private static @Nullable String externalPath(@Nullable String prefix, @NotNull String requestPath) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        String checked = prefix.strip();
        if (!checked.startsWith("/")
                || checked.contains("?")
                || checked.contains("#")
                || checked.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        String normalizedPrefix = checked.endsWith("/") ? checked.substring(0, checked.length() - 1) : checked;
        return normalizedPrefix + (requestPath.startsWith("/") ? requestPath : "/" + requestPath);
    }

    /** 对业务代码隐藏 Gateway 控制 Secret 和所有客户端不可声明的转发 Header。 */
    private static class HiddenGatewayHeadersRequest extends HttpServletRequestWrapper {

        HiddenGatewayHeadersRequest(@NotNull HttpServletRequest request) {
            super(request);
        }

        @Override
        public @Nullable String getHeader(@NotNull String name) {
            return hidden(name) ? null : super.getHeader(name);
        }

        @Override
        public @NotNull Enumeration<String> getHeaders(@NotNull String name) {
            return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public @NotNull Enumeration<String> getHeaderNames() {
            Enumeration<String> names = super.getHeaderNames();
            if (names == null) {
                return Collections.emptyEnumeration();
            }
            List<String> visible = new ArrayList<>();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (!hidden(name)) {
                    visible.add(name);
                }
            }
            return Collections.enumeration(visible);
        }

        /** 判断 Header 是否只允许可信 Gateway 在过滤器内部使用。 */
        private static boolean hidden(@NotNull String name) {
            return name.regionMatches(true, 0, GatewayHttpHeaders.PREFIX, 0, GatewayHttpHeaders.PREFIX.length())
                    || name.regionMatches(
                            true,
                            0,
                            GatewayHttpHeaders.FORWARDED_PREFIX,
                            0,
                            GatewayHttpHeaders.FORWARDED_PREFIX.length())
                    || GatewayHttpHeaders.FORWARDED.equalsIgnoreCase(name);
        }
    }
}
