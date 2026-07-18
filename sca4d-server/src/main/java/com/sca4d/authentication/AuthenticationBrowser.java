package com.sca4d.authentication;

import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 读取或签发 HttpOnly Cookie，并生成 Flowable 保存的浏览器摘要。 */
@Component
public class AuthenticationBrowser {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Sca4djAuthenticationProperties properties;

    /** @param properties Cookie 名称和认证有效期配置 */
    public AuthenticationBrowser(@NotNull Sca4djAuthenticationProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取当前浏览器 Cookie；缺失时生成新随机值并写入响应。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 只用于认证浏览器绑定判断的 SHA-256 摘要
     */
    public @NotNull String bind(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        String token = cookie(request);
        if (token == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        ResponseCookie cookie = ResponseCookie.from(properties.getBrowserCookie(), token)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.getTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return hash(token);
    }

    /**
     * 读取当前浏览器已有 Cookie，不签发新值。
     *
     * @param request 当前 HTTP 请求
     * @return Cookie 摘要；缺失或格式无效时为固定空摘要
     */
    public @NotNull String require(@NotNull HttpServletRequest request) {
        String token = cookie(request);
        return hash(token == null ? "" : token);
    }

    /** @return 当前请求中长度有效的浏览器 Cookie；不存在时为 null */
    private String cookie(@NotNull HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getBrowserCookie().equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()
                    && cookie.getValue().length() <= 128) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** @return 浏览器随机值的 SHA-256 十六进制摘要 */
    private @NotNull String hash(@NotNull String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", exception);
        }
    }
}
