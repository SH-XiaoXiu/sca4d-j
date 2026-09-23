package com.sca4d.gateway.loki;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 保存可选 Loki 写入代理的入口、目标地址和独立凭据。 */
@ConfigurationProperties("sca4dj.gateway.logging")
public final class LokiGatewayProperties {

    private boolean enabled;

    private @Nullable String lokiOrigin;

    private @NotNull String pushPath = "/internal/logging/push";

    private @NotNull String lokiPath = "/loki/api/v1/push";

    private @Nullable String username;

    private @Nullable String password;

    /** @return 是否注册 Loki 日志写入代理路由 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled true 表示校验配置并注册写入路由 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return Gateway 可以访问的 Loki HTTP(S) Origin */
    public @Nullable String getLokiOrigin() {
        return lokiOrigin;
    }

    /** @param lokiOrigin 不带用户信息、路径、查询或 fragment 的 Loki Origin */
    public void setLokiOrigin(@Nullable String lokiOrigin) {
        this.lokiOrigin = lokiOrigin;
    }

    /** @return Alloy 提交日志时调用的 Gateway 绝对路径 */
    public @NotNull String getPushPath() {
        return pushPath;
    }

    /** @param pushPath Gateway 对外开放的单一日志写入路径 */
    public void setPushPath(@NotNull String pushPath) {
        this.pushPath = pushPath;
    }

    /** @return Loki 接收 push 请求的内部绝对路径 */
    public @NotNull String getLokiPath() {
        return lokiPath;
    }

    /** @param lokiPath 相对 Loki Origin 的写入路径 */
    public void setLokiPath(@NotNull String lokiPath) {
        this.lokiPath = lokiPath;
    }

    /** @return Alloy 提交 Basic Auth 时使用的用户名 */
    public @Nullable String getUsername() {
        return username;
    }

    /** @param username 不含冒号和控制字符的 Basic Auth 用户名 */
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    /** @return Alloy 提交 Basic Auth 时使用的密码 */
    public @Nullable String getPassword() {
        return password;
    }

    /** @param password 不得写入日志的 Basic Auth 密码 */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    /** 校验并规范化启用状态下的全部配置。 */
    public void validate(@NotNull Environment environment) {
        lokiOrigin = validatedLokiOrigin().toString();
        pushPath = validatedPath(pushPath, "sca4dj.gateway.logging.push-path");
        lokiPath = validatedPath(lokiPath, "sca4dj.gateway.logging.loki-path");
        username = validatedUsername();
        password = validatedPassword();
        String gatewaySecret = environment.getProperty("sca4dj.gateway.secret");
        if (gatewaySecret != null
                && MessageDigest.isEqual(
                        gatewaySecret.strip().getBytes(StandardCharsets.UTF_8),
                        password.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Loki 写入密码不能与 Gateway Secret 相同");
        }
    }

    /** @return 已校验且不带结尾斜杠的 Loki Origin */
    @CheckReturnValue
    public @NotNull URI validatedLokiOrigin() {
        String value = requireText(lokiOrigin, "sca4dj.gateway.logging.loki-origin");
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("sca4dj.gateway.logging.loki-origin 必须是有效 URI", failure);
        }
        String path = uri.getRawPath();
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || path != null && !path.isEmpty() && !"/".equals(path)
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalStateException("sca4dj.gateway.logging.loki-origin 必须是 HTTP(S) Origin");
        }
        return "/".equals(path) ? URI.create(value.substring(0, value.length() - 1)) : uri;
    }

    /** @return 去除首尾空白后的 Loki 写入用户名 */
    @CheckReturnValue
    public @NotNull String validatedUsername() {
        String value = requireText(username, "sca4dj.gateway.logging.username");
        if (value.indexOf(':') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("sca4dj.gateway.logging.username 不能包含冒号或控制字符");
        }
        return value;
    }

    /** @return 保留原始字符的 Loki 写入密码 */
    @CheckReturnValue
    public @NotNull String validatedPassword() {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("sca4dj.gateway.logging.password 不能为空");
        }
        if (password.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("sca4dj.gateway.logging.password 不能包含控制字符");
        }
        return password;
    }

    /** 返回不包含模式字符、查询或 fragment 的绝对路径。 */
    private static @NotNull String validatedPath(@Nullable String value, @NotNull String name) {
        String path = requireText(value, name);
        if (!path.startsWith("/")
                || path.contains("?")
                || path.contains("#")
                || path.contains("*")
                || path.contains("{")
                || path.contains("}")
                || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException(name + " 必须是没有模式、查询或 fragment 的绝对路径");
        }
        return path;
    }

    /** 返回去除首尾空白后的必填文本。 */
    private static @NotNull String requireText(@Nullable String value, @NotNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 不能为空");
        }
        return value.strip();
    }
}
