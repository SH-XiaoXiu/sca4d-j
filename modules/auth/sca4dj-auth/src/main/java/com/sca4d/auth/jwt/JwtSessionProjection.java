package com.sca4d.auth.jwt;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.sca4d.auth.config.AuthProperties;
import com.sca4d.auth.config.AuthSettings;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.core.api.authentication.AuthTokenMode;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionTokens;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 使用独立 HS256 密钥把完整 Core Session 编码为短效 JWS 投影，并在请求中离线恢复。 */
public final class JwtSessionProjection {

    static final String TOKEN_TYPE = "Bearer";

    static final String JWT_TYPE = "session+jwt";

    static final String SESSION_CLAIM = "session";

    static final String SESSION_ID_CLAIM = "sid";

    private final SessionCodec codec;

    private final AuthTokenMode mode;

    private final String activeKeyId;

    private final Map<String, JWTSigner> signers;

    private final String issuer;

    private final String audience;

    private final AuthSettings settings;

    private final int maxTokenLength;

    private final Clock clock;

    /**
     * 创建 JWT 投影服务并验证 JWT 模式需要的全部签名配置。
     *
     * @param codec Session wire 编解码器
     * @param properties Auth Token 和 JWT 配置
     * @param settings 每次签发和验证时读取 access TTL 的 Settings
     * @param clock JWT 签发和验证时间来源
     * @throws IllegalStateException JWT 模式缺少有效 active key 或密钥不足 32 字节时抛出
     */
    public JwtSessionProjection(
            @NotNull SessionCodec codec,
            @NotNull AuthProperties properties,
            @NotNull AuthSettings settings,
            @NotNull Clock clock) {
        this.codec = Objects.requireNonNull(codec, "Session 编解码器不能为空");
        AuthProperties checkedProperties = Objects.requireNonNull(properties, "Auth 配置不能为空");
        this.mode = checkedProperties.getTokenMode();
        AuthProperties.Jwt jwt = checkedProperties.getJwt();
        this.settings = Objects.requireNonNull(settings, "Settings 不能为空");
        this.maxTokenLength = jwt.getMaxTokenLength();
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        if (mode == AuthTokenMode.JWT) {
            this.activeKeyId = requireText(jwt.getActiveKeyId(), "JWT active key id");
            this.issuer = requireText(jwt.getIssuer(), "JWT issuer");
            this.audience = requireText(jwt.getAudience(), "JWT audience");
            this.signers = loadSigners(jwt.getKeys());
            if (!signers.containsKey(activeKeyId)) {
                throw new IllegalStateException("JWT active-key-id 必须对应一个已配置密钥");
            }
        } else {
            this.activeKeyId = "";
            this.issuer = "";
            this.audience = "";
            this.signers = Map.of();
        }
    }

    /** @return 普通请求默认使用的 Token 模式 */
    public @NotNull AuthTokenMode mode() {
        return mode;
    }

    /**
     * 为已经在线建立或恢复的 Session 生成客户端 Token 集合。
     *
     * @param session 完整 Core Session
     * @param sessionToken backing Session 的不透明 Token
     * @return SESSION 模式复用不透明 Token；JWT 模式同时返回短效 JWT 和 Session Token
     */
    @CheckReturnValue
    public @NotNull SessionTokens issue(@NotNull Session session, @NotNull String sessionToken) {
        Session checkedSession = Objects.requireNonNull(session, "Session 不能为空");
        String checkedSessionToken = requireText(sessionToken, "Session Token");
        if (mode == AuthTokenMode.SESSION) {
            return new SessionTokens(
                    mode,
                    TOKEN_TYPE,
                    checkedSessionToken,
                    checkedSession.expiresAt(),
                    null,
                    checkedSession.id().value(),
                    checkedSession.expiresAt());
        }

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Duration accessTtl = settings.JWT_ACCESS_TTL.get();
        Instant accessExpiresAt = issuedAt.plus(accessTtl);
        if (accessExpiresAt.isAfter(checkedSession.expiresAt())) {
            accessExpiresAt = checkedSession.expiresAt();
        }
        if (!accessExpiresAt.isAfter(issuedAt)) {
            throw new IllegalStateException("Session 剩余时间不足以签发 JWT");
        }
        JWTSigner signer = signers.get(activeKeyId);
        String token = JWT.create()
                .setHeader(JWTHeader.TYPE, JWT_TYPE)
                .setHeader(JWTHeader.KEY_ID, activeKeyId)
                .setIssuer(issuer)
                .setAudience(audience)
                .setSubject(Long.toString(checkedSession.account().accountId()))
                .setIssuedAt(Date.from(issuedAt))
                .setExpiresAt(Date.from(accessExpiresAt))
                .setPayload(SESSION_ID_CLAIM, checkedSession.id().value())
                .setPayload(SESSION_CLAIM, codec.encode(checkedSession))
                .setSigner(signer)
                .sign(false);
        if (token.length() > maxTokenLength) {
            throw new IllegalStateException("JWT 超过允许的最大长度");
        }
        return new SessionTokens(
                mode,
                TOKEN_TYPE,
                token,
                accessExpiresAt,
                checkedSessionToken,
                checkedSession.id().value(),
                checkedSession.expiresAt());
    }

    /**
     * 离线验证并恢复短效 JWT 中的完整 Session，不读取 Redis 或数据库。
     *
     * @param token JWT access Token
     * @return 签名、声明、有效期和 Session wire 均有效时返回 Session，否则返回空结果
     */
    @CheckReturnValue
    public @NotNull Optional<Session> restore(@NotNull String token) {
        String checkedToken = Objects.requireNonNull(token, "JWT 不能为空");
        if (mode != AuthTokenMode.JWT || checkedToken.isBlank() || checkedToken.length() > maxTokenLength) {
            return Optional.empty();
        }
        try {
            JWT jwt = JWT.of(checkedToken);
            if (!"HS256".equals(jwt.getAlgorithm()) || !JWT_TYPE.equals(claimText(jwt.getHeader(JWTHeader.TYPE)))) {
                return Optional.empty();
            }
            String keyId = claimText(jwt.getHeader(JWTHeader.KEY_ID));
            JWTSigner signer = signers.get(keyId);
            if (signer == null) {
                return Optional.empty();
            }
            Instant now = clock.instant();
            JWTValidator.of(jwt)
                    .validateAlgorithm(signer)
                    .validateDate(Date.from(now));

            Instant issuedAt = claimInstant(jwt.getPayload(JWTPayload.ISSUED_AT));
            Instant expiresAt = claimInstant(jwt.getPayload(JWTPayload.EXPIRES_AT));
            Duration accessTtl = settings.JWT_ACCESS_TTL.get();
            if (!expiresAt.isAfter(now)
                    || !expiresAt.isAfter(issuedAt)
                    || expiresAt.isAfter(issuedAt.plus(accessTtl))
                    || !issuer.equals(claimText(jwt.getPayload(JWTPayload.ISSUER)))
                    || !audienceMatches(jwt.getPayload(JWTPayload.AUDIENCE))) {
                return Optional.empty();
            }

            String encodedSession = claimText(jwt.getPayload(SESSION_CLAIM));
            Session session = codec.decode(encodedSession);
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
        for (Map.Entry<String, String> entry : Objects.requireNonNull(configuredKeys, "JWT keys 不能为空").entrySet()) {
            String keyId = requireText(entry.getKey(), "JWT key id");
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(requireText(entry.getValue(), "JWT key"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("JWT key 必须使用 Base64 编码: " + keyId, exception);
            }
            if (key.length < 32) {
                throw new IllegalStateException("JWT HS256 key 解码后不能少于 32 字节: " + keyId);
            }
            loaded.put(keyId, JWTSignerUtil.hs256(key));
        }
        return Map.copyOf(loaded);
    }

    private boolean audienceMatches(Object claim) {
        if (audience.equals(claimTextOrNull(claim))) {
            return true;
        }
        if (claim instanceof Iterable<?> values) {
            for (Object value : values) {
                if (audience.equals(claimTextOrNull(value))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Instant claimInstant(Object claim) {
        if (claim instanceof Number value) {
            return Instant.ofEpochSecond(value.longValue());
        }
        throw new IllegalArgumentException("JWT 时间声明必须为整数");
    }

    private static String claimText(Object claim) {
        String value = claimTextOrNull(claim);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT 文本声明不能为空");
        }
        return value;
    }

    private static String claimTextOrNull(Object claim) {
        return claim instanceof String value ? value : null;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "不能为空");
        }
        return value;
    }
}
