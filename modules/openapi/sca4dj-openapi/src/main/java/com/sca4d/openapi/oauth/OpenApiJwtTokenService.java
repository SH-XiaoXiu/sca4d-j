package com.sca4d.openapi.oauth;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.openapi.config.OpenApiProperties;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 使用独立 HS256 密钥签发和恢复 OpenAPI access JWT，不改变浏览器 Session 的 Token 模式。 */
public class OpenApiJwtTokenService {

    private static final String JWT_TYPE = "openapi+jwt";

    private static final String SESSION_CLAIM = "session";

    private static final String SESSION_ID_CLAIM = "sid";

    private final @NotNull Clock clock;

    private final @NotNull OpenApiProperties.Jwt properties;

    private final @NotNull Map<String, JWTSigner> signers;

    private final @NotNull SessionCodec codec;

    /** 创建 OpenAPI JWT 服务；未启用时不要求配置密钥。 */
    public OpenApiJwtTokenService(
            @NotNull SessionCodec codec,
            @NotNull OpenApiProperties properties,
            @NotNull Clock clock) {
        this.codec = Objects.requireNonNull(codec, "Session 编解码器不能为空");
        this.properties = Objects.requireNonNull(properties, "OpenAPI 配置不能为空").getJwt();
        this.clock = Objects.requireNonNull(clock, "OpenAPI JWT 时钟不能为空");
        this.signers = properties.getJwt().isEnabled() ? loadSigners(this.properties.getKeys()) : Map.of();
        if (this.properties.isEnabled()) {
            requireText(this.properties.getActiveKeyId(), "OpenAPI JWT active key id");
            requireText(this.properties.getIssuer(), "OpenAPI JWT issuer");
            requireText(this.properties.getAudience(), "OpenAPI JWT audience");
            if (!signers.containsKey(this.properties.getActiveKeyId())) {
                throw new IllegalStateException("OpenAPI JWT active-key-id 必须对应一个有效密钥");
            }
        }
    }

    /** @return 是否已启用独立 OpenAPI JWT */
    public boolean enabled() {
        return properties.isEnabled();
    }

    /** 为已经建立的 Session 签发独立 OpenAPI access JWT。 */
    @CheckReturnValue
    public @NotNull IssuedOpenApiJwt issue(@NotNull Session session) {
        if (!enabled()) {
            throw new IllegalStateException("OpenAPI 独立 JWT 未启用");
        }
        Session checked = Objects.requireNonNull(session, "Session 不能为空");
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.getAccessTtl());
        JWTSigner signer = signers.get(properties.getActiveKeyId());
        String token = JWT.create()
                .setHeader(JWTHeader.TYPE, JWT_TYPE)
                .setHeader(JWTHeader.KEY_ID, properties.getActiveKeyId())
                .setIssuer(properties.getIssuer())
                .setAudience(properties.getAudience())
                .setSubject(Long.toString(checked.account().accountId()))
                .setIssuedAt(Date.from(issuedAt))
                .setExpiresAt(Date.from(expiresAt))
                .setPayload(SESSION_ID_CLAIM, checked.id().value())
                .setPayload(SESSION_CLAIM, codec.encode(checked))
                .setSigner(signer)
                .sign(false);
        if (token.length() > properties.getMaxTokenLength()) {
            throw new IllegalStateException("OpenAPI JWT 超过允许的最大长度");
        }
        return new IssuedOpenApiJwt(token, expiresAt);
    }

    /** 离线验证并恢复独立 OpenAPI access JWT 中的完整 Session。 */
    @CheckReturnValue
    public @NotNull Optional<Session> restore(@NotNull String token) {
        if (!enabled()) {
            return Optional.empty();
        }
        String checked = Objects.requireNonNull(token, "OpenAPI JWT 不能为空");
        if (checked.isBlank() || checked.length() > properties.getMaxTokenLength()) {
            return Optional.empty();
        }
        try {
            JWT jwt = JWT.of(checked);
            if (!"HS256".equals(jwt.getAlgorithm())
                    || !JWT_TYPE.equals(claimText(jwt.getHeader(JWTHeader.TYPE)))) {
                return Optional.empty();
            }
            JWTSigner signer = signers.get(claimText(jwt.getHeader(JWTHeader.KEY_ID)));
            if (signer == null) {
                return Optional.empty();
            }
            Instant now = clock.instant();
            JWTValidator.of(jwt).validateAlgorithm(signer).validateDate(Date.from(now));
            Instant issuedAt = claimInstant(jwt.getPayload(JWTPayload.ISSUED_AT));
            Instant expiresAt = claimInstant(jwt.getPayload(JWTPayload.EXPIRES_AT));
            if (!expiresAt.isAfter(now)
                    || !expiresAt.isAfter(issuedAt)
                    || expiresAt.isAfter(issuedAt.plus(properties.getAccessTtl()))
                    || !properties.getIssuer().equals(claimText(jwt.getPayload(JWTPayload.ISSUER)))
                    || !audienceMatches(jwt.getPayload(JWTPayload.AUDIENCE))) {
                return Optional.empty();
            }
            Session session = codec.decode(claimText(jwt.getPayload(SESSION_CLAIM)));
            if (now.isBefore(session.issuedAt())
                    || !now.isBefore(session.expiresAt())
                    || expiresAt.isAfter(session.expiresAt())
                    || !session.id().value().equals(claimText(jwt.getPayload(SESSION_ID_CLAIM)))
                    || !Long.toString(session.account().accountId())
                            .equals(claimText(jwt.getPayload(JWTPayload.SUBJECT)))) {
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, JWTSigner> loadSigners(Map<String, String> configuredKeys) {
        LinkedHashMap<String, JWTSigner> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : Objects.requireNonNull(configuredKeys, "OpenAPI JWT 密钥不能为空")
                .entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(entry.getValue().trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("OpenAPI JWT 密钥必须使用 Base64 编码: " + entry.getKey(), exception);
            }
            if (key.length < 32) {
                throw new IllegalStateException("OpenAPI JWT HS256 密钥解码后不能少于 32 字节: " + entry.getKey());
            }
            loaded.put(requireText(entry.getKey(), "OpenAPI JWT key id"), JWTSignerUtil.hs256(key));
        }
        return Map.copyOf(loaded);
    }

    private boolean audienceMatches(Object value) {
        if (properties.getAudience().equals(value)) {
            return true;
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (properties.getAudience().equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Instant claimInstant(Object value) {
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        throw new IllegalArgumentException("OpenAPI JWT 时间声明必须为整数");
    }

    private static String claimText(Object value) {
        return requireText(value instanceof String text ? text : null, "OpenAPI JWT 声明");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "不能为空");
        }
        return value;
    }

    /** 保存一次性返回给 OAuth 调用方的 OpenAPI JWT 和过期时间。 */
    public static class IssuedOpenApiJwt {

        private final @NotNull Instant expiresAt;

        private final @NotNull String token;

        public IssuedOpenApiJwt(@NotNull String token, @NotNull Instant expiresAt) {
            this.token = Objects.requireNonNull(token, "OpenAPI JWT 不能为空");
            this.expiresAt = Objects.requireNonNull(expiresAt, "OpenAPI JWT 过期时间不能为空");
        }

        /** @return 不能写入日志的 OpenAPI access JWT */
        public @NotNull String token() {
            return token;
        }

        /** @return JWT 过期时间 */
        public @NotNull Instant expiresAt() {
            return expiresAt;
        }
    }
}
