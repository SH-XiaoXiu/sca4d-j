package com.sca4d.open.oauth;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 声明具体应用选择公开的 Open 认证端点路径和认证流程。 */
public class OpenAuthenticationEndpoint {

    private final @NotNull String path;

    private OpenAuthenticationEndpoint(@NotNull String path) {
        this.path = requirePath(path);
    }

    /**
     * 声明使用 OAuth 2.0 Client Credentials 的 Token 端点。
     *
     * <p>端点固定接受 {@code POST application/x-www-form-urlencoded}，使用 HTTP Basic 传递 Client ID 和
     * Shared Secret。Open 模块负责参数校验、Client 状态检查、Secret 认证、Session 建立和 Token 签发。
     *
     * @param path 具体应用选择的绝对 HTTP 路径，不包含查询字符串或片段
     * @return Client Credentials 认证端点配置
     */
    @Contract("_ -> new")
    public static @NotNull OpenAuthenticationEndpoint clientCredentials(@NotNull String path) {
        return new OpenAuthenticationEndpoint(path);
    }

    /** @return 具体应用选择的认证端点绝对路径 */
    public @NotNull String path() {
        return path;
    }

    /** 校验认证端点使用的绝对 HTTP 路径。 */
    private static @NotNull String requirePath(@NotNull String value) {
        String path = Objects.requireNonNull(value, "Open 认证端点路径不能为空").trim();
        if (!path.startsWith("/") || "/".equals(path)) {
            throw new IllegalArgumentException("Open 认证端点必须是非根绝对路径: " + path);
        }
        if (path.endsWith("/") || path.contains("//") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException(
                    "Open 认证端点不能以斜杠结尾或包含重复斜杠、查询字符串和片段: " + path);
        }
        return path;
    }
}
