package com.sca4d.config.properties;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/** 认证有效期、浏览器 Cookie 和各认证方式的配置。 */
@Data
@ConfigurationProperties(prefix = "sca4dj.authentication")
public class Sca4djAuthenticationProperties {

    /** 一次认证从创建到完成允许保留的时间。 */
    private Duration ttl = Duration.ofMinutes(10);

    /** 已完成 STEP_UP 在当前浏览器内可授权敏感操作的时间。 */
    private Duration stepUpTtl = Duration.ofMinutes(5);

    /** 绑定认证请求与浏览器的 HttpOnly Cookie 名称。 */
    private String browserCookie = "sca4d_auth_browser";

    /** 邮件验证码签发、投递和验证配置。 */
    private EmailCode emailCode = new EmailCode();

    /** TOTP enrollment 和二维码配置。 */
    private Totp totp = new Totp();

    /** Passkey RP、Origin 和 ceremony 共享存储配置。 */
    private Passkey passkey = new Passkey();

    /** 邮件验证码认证方式配置。 */
    @Data
    public static class EmailCode {

        /** 是否注册 EMAIL_CODE AuthMethod 和对应的第二因素检查。 */
        private boolean enabled;

        /** Challenge Redis key 前缀。 */
        private String challengeKeyPrefix = "sca4dj:authentication:email-code";

        /** 单个验证码的有效期。 */
        private Duration ttl = Duration.ofMinutes(5);

        /** 同一次认证再次发送前必须等待的时间。 */
        private Duration resendInterval = Duration.ofSeconds(60);

        /** 单个验证码最多允许的错误提交次数。 */
        private int maxAttempts = 5;

        /** 发送认证邮件使用的启用 Sender 主键。 */
        private Long senderId;

        /** 邮件模板 family。 */
        private String templateFamily = "authentication.email-code";

        /** 启用邮件验证码时检查 Redis、Sender、模板和尝试次数配置。 */
        public void validate() {
            if (!enabled) {
                return;
            }
            if (challengeKeyPrefix == null || challengeKeyPrefix.isBlank()) {
                throw new IllegalArgumentException("邮件验证码 Redis key 前缀不能为空");
            }
            if (senderId == null || senderId <= 0) {
                throw new IllegalArgumentException("启用邮件验证码时必须配置有效 sender-id");
            }
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("邮件验证码 TTL 必须大于 0");
            }
            if (resendInterval == null || resendInterval.isZero() || resendInterval.isNegative()) {
                throw new IllegalArgumentException("邮件验证码重发间隔必须大于 0");
            }
            if (maxAttempts < 1 || maxAttempts > 20) {
                throw new IllegalArgumentException("邮件验证码最大尝试次数必须在 1 到 20 之间");
            }
            if (templateFamily == null || templateFamily.isBlank()) {
                throw new IllegalArgumentException("邮件验证码模板 family 不能为空");
            }
        }
    }

    public void setEmailCode(@NotNull EmailCode emailCode) {
        this.emailCode = emailCode;
    }

    /** TOTP enrollment 共享存储和二维码展示配置。 */
    @Data
    public static class Totp {

        /** enrollment Redis key 前缀。 */
        private String enrollmentKeyPrefix = "sca4dj:authentication:totp-enrollment";

        /** enrollment 从创建到激活允许保留的时间。 */
        private Duration enrollmentTtl = Duration.ofMinutes(10);

        /** 身份验证器二维码显示的发行者名称。 */
        private String issuer = "SCA4D";

        /** 启动时拒绝无法共享、无法过期或无法生成二维码的配置。 */
        public void validate() {
            if (enrollmentKeyPrefix == null || enrollmentKeyPrefix.isBlank()) {
                throw new IllegalArgumentException("TOTP enrollment Redis key 前缀不能为空");
            }
            if (enrollmentTtl == null || enrollmentTtl.isZero() || enrollmentTtl.isNegative()) {
                throw new IllegalArgumentException("TOTP enrollment TTL 必须大于 0");
            }
            if (issuer == null || issuer.isBlank() || issuer.length() > 64) {
                throw new IllegalArgumentException("TOTP issuer 长度必须为 1 到 64 个字符");
            }
        }
    }

    public void setTotp(@NotNull Totp totp) {
        this.totp = totp;
    }

    /** WebAuthn RP 和浏览器 ceremony 配置。 */
    @Data
    public static class Passkey {

        /** 是否注册 Passkey AuthMethod、RP 和对应 REST 资源。 */
        private boolean enabled;

        /** 注册 ceremony Redis key 前缀。 */
        private String registrationKeyPrefix = "sca4dj:authentication:passkey-registration";

        /** assertion ceremony Redis key 前缀。 */
        private String assertionKeyPrefix = "sca4dj:authentication:passkey-assertion";

        /** 注册和 assertion ceremony 的有效期。 */
        private Duration ceremonyTtl = Duration.ofMinutes(5);

        /** WebAuthn Relying Party ID，通常是应用域名。 */
        private String rpId;

        /** 浏览器和认证器显示的 Relying Party 名称。 */
        private String rpName = "SCA4D";

        /** 允许提交 WebAuthn 响应的精确 Origin 集合。 */
        private Set<String> origins = new LinkedHashSet<>();

        /** 启用 Passkey 时拒绝不完整或会放宽 Origin 检查的配置。 */
        public void validate() {
            if (!enabled) {
                return;
            }
            if (registrationKeyPrefix == null || registrationKeyPrefix.isBlank()) {
                throw new IllegalArgumentException("Passkey registration Redis key 前缀不能为空");
            }
            if (assertionKeyPrefix == null || assertionKeyPrefix.isBlank()) {
                throw new IllegalArgumentException("Passkey assertion Redis key 前缀不能为空");
            }
            if (registrationKeyPrefix.equals(assertionKeyPrefix)) {
                throw new IllegalArgumentException("Passkey registration 与 assertion 必须使用不同 Redis key 前缀");
            }
            if (ceremonyTtl == null || ceremonyTtl.isZero() || ceremonyTtl.isNegative()) {
                throw new IllegalArgumentException("Passkey ceremony TTL 必须大于 0");
            }
            if (rpId == null
                    || rpId.isBlank()
                    || rpId.length() > 253
                    || rpId.contains(":")
                    || rpId.contains("/")) {
                throw new IllegalArgumentException("Passkey RP ID 必须是不含协议、端口和路径的有效主机名");
            }
            if (rpName == null || rpName.isBlank() || rpName.length() > 64) {
                throw new IllegalArgumentException("Passkey RP 名称长度必须为 1 到 64 个字符");
            }
            if (origins == null || origins.isEmpty()) {
                throw new IllegalArgumentException("启用 Passkey 时必须配置至少一个精确 Origin");
            }
            for (String value : origins) {
                URI origin;
                try {
                    origin = URI.create(value);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Passkey Origin 必须是有效 URI", exception);
                }
                boolean local = "localhost".equals(origin.getHost()) || "127.0.0.1".equals(origin.getHost());
                if (origin.getHost() == null
                        || origin.getUserInfo() != null
                        || origin.getPath() == null
                        || !origin.getPath().isEmpty()
                        || origin.getQuery() != null
                        || origin.getFragment() != null
                        || (!"https".equals(origin.getScheme()) && !(local && "http".equals(origin.getScheme())))) {
                    throw new IllegalArgumentException("Passkey Origin 必须是精确 HTTPS Origin；本机开发允许 HTTP");
                }
                if (!origin.getHost().equals(rpId) && !origin.getHost().endsWith("." + rpId)) {
                    throw new IllegalArgumentException("Passkey Origin 主机必须等于 RP ID 或位于其子域名");
                }
            }
        }
    }

    public void setPasskey(@NotNull Passkey passkey) {
        this.passkey = passkey;
    }
}
