package com.sca4d.app.server.authentication;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** 从服务端 HttpSession 派生不可逆浏览器绑定，不向前端暴露 Session ID。 */
@Component
public final class FederatedBrowserBinding {

    /** 计算当前浏览器的稳定绑定摘要。 */
    public @NotNull String binding(@NotNull HttpServletRequest request) {
        String sessionId = Objects.requireNonNull(request, "HTTP 请求不能为空").getSession(true).getId();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(("sca4dj:federated:" + sessionId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算联邦浏览器绑定", exception);
        }
    }
}
