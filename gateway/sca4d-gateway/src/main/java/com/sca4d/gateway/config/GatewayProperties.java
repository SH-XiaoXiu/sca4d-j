package com.sca4d.gateway.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/** 保存 Gateway 注册控制、Server readiness 检查和可选日志写入参数。 */
@ConfigurationProperties("sca4dj.gateway")
public final class GatewayProperties {

    private @Nullable String secret;

    private @NotNull Duration leaseTtl = Duration.ofSeconds(30);

    private @NotNull Duration healthInterval = Duration.ofSeconds(5);

    private @NotNull Duration healthTimeout = Duration.ofSeconds(3);

    private final @NotNull Logging logging = new Logging();

    /** @return 控制 API 和转发到 Server 时使用的 Secret */
    public @Nullable String getSecret() {
        return secret;
    }

    /** @param secret 不得转发给外部调用方或写入日志的 Gateway Secret */
    public void setSecret(@Nullable String secret) {
        this.secret = secret;
    }

    /** @return Server 未续租后保留注册的时长 */
    public @NotNull Duration getLeaseTtl() {
        return leaseTtl;
    }

    /** @param leaseTtl 大于 Server 续租间隔的正数时长 */
    public void setLeaseTtl(@NotNull Duration leaseTtl) {
        this.leaseTtl = leaseTtl;
    }

    /** @return Gateway 主动检查 Server readiness 的间隔 */
    public @NotNull Duration getHealthInterval() {
        return healthInterval;
    }

    /** @param healthInterval 正数检查间隔 */
    public void setHealthInterval(@NotNull Duration healthInterval) {
        this.healthInterval = healthInterval;
    }

    /** @return 单次 readiness 检查的请求超时 */
    public @NotNull Duration getHealthTimeout() {
        return healthTimeout;
    }

    /** @param healthTimeout 正数 HTTP 请求超时 */
    public void setHealthTimeout(@NotNull Duration healthTimeout) {
        this.healthTimeout = healthTimeout;
    }

    /** @return Gateway 接收日志写入并转发到 Loki 的配置 */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring Boot 必须通过同一个 final 嵌套配置对象绑定 sca4dj.gateway.logging")
    public @NotNull Logging getLogging() {
        return logging;
    }

    /** @return 去除首尾空白后的必填 Gateway Secret */
    @CheckReturnValue
    public @NotNull String requireSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("sca4dj.gateway.secret 不能为空");
        }
        return secret.strip();
    }

    /**
     * 校验租约、健康检查和已启用日志写入配置。
     *
     * @throws IllegalStateException 任一必填值缺失或格式不合法时抛出
     */
    public void validate() {
        positive(leaseTtl, "sca4dj.gateway.lease-ttl");
        positive(healthInterval, "sca4dj.gateway.health-interval");
        positive(healthTimeout, "sca4dj.gateway.health-timeout");
        logging.validate();
        if (logging.isEnabled()
                && MessageDigest.isEqual(
                        requireSecret().getBytes(StandardCharsets.UTF_8),
                        logging.requirePassword().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Loki 写入密码不能与 Gateway Secret 相同");
        }
    }

    /** 校验配置 Duration 为正数。 */
    private static void positive(@Nullable Duration value, @NotNull String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " 必须为正数");
        }
    }

    /** 保存可选 Loki 写入路由的目标地址和独立凭据。 */
    public static final class Logging {

        private boolean enabled;

        private @Nullable String lokiOrigin;

        private @Nullable String username;

        private @Nullable String password;

        /** @return 是否注册 Loki 日志写入路由 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 为 true 时要求 Loki Origin 和写入凭据完整有效 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return Gateway 可以访问的 Loki HTTP(S) Origin */
        public @Nullable String getLokiOrigin() {
            return lokiOrigin;
        }

        /** @param lokiOrigin 不带用户信息、业务路径、查询或 fragment 的 Loki HTTP(S) Origin */
        public void setLokiOrigin(@Nullable String lokiOrigin) {
            this.lokiOrigin = lokiOrigin;
        }

        /** @return Alloy 提交 Basic Auth 时使用的用户名 */
        public @Nullable String getUsername() {
            return username;
        }

        /** @param username 不含冒号和控制字符的非空 Basic Auth 用户名 */
        public void setUsername(@Nullable String username) {
            this.username = username;
        }

        /** @return Alloy 提交 Basic Auth 时使用的密码 */
        public @Nullable String getPassword() {
            return password;
        }

        /** @param password 不得写入日志的非空 Basic Auth 密码 */
        public void setPassword(@Nullable String password) {
            this.password = password;
        }

        /** @return 已校验且不带结尾斜杠的 Loki Origin */
        @CheckReturnValue
        public @NotNull URI requireLokiOrigin() {
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
                    || (path != null && !path.isEmpty() && !"/".equals(path))
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalStateException("sca4dj.gateway.logging.loki-origin 必须是 HTTP(S) Origin");
            }
            return "/".equals(path) ? URI.create(value.substring(0, value.length() - 1)) : uri;
        }

        /** @return 去除首尾空白后的 Loki 写入用户名 */
        @CheckReturnValue
        public @NotNull String requireUsername() {
            String value = requireText(username, "sca4dj.gateway.logging.username");
            if (value.indexOf(':') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalStateException("sca4dj.gateway.logging.username 不能包含冒号或控制字符");
            }
            return value;
        }

        /** @return 保留原始字符的 Loki 写入密码 */
        @CheckReturnValue
        public @NotNull String requirePassword() {
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("sca4dj.gateway.logging.password 不能为空");
            }
            if (password.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalStateException("sca4dj.gateway.logging.password 不能包含控制字符");
            }
            return password;
        }

        /** 启用日志写入路由时校验并规范化目标地址和凭据。 */
        private void validate() {
            if (!enabled) {
                return;
            }
            lokiOrigin = requireLokiOrigin().toString();
            username = requireUsername();
            password = requirePassword();
        }

        /** 返回去除首尾空白后的必填文本。 */
        private static @NotNull String requireText(@Nullable String value, @NotNull String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(name + " 不能为空");
            }
            return value.strip();
        }
    }
}
