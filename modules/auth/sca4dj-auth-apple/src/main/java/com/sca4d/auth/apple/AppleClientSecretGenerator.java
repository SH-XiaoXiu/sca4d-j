package com.sca4d.auth.apple;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.core.api.error.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

/**
 * 签发 Sign in with Apple 的 {@code client_secret}：一个由 Apple Developer 私钥签名的短期 JWT。
 *
 * <p>签名材料是 Provider 配置里保存的 Client Secret，格式为 JSON 对象
 * {@code {"teamId":"...","keyId":"...","privateKey":"-----BEGIN PRIVATE KEY-----..."}}，
 * 其中 {@code privateKey} 是 Apple 下载的 {@code .p8} 文件内容。JWT 头部为 {@code alg=ES256} 和 {@code kid=keyId}，
 * claims 为 {@code iss=teamId}、{@code sub=Client ID}、{@code aud=Provider 的 issuer}，有效期 5 分钟。
 */
public final class AppleClientSecretGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(AppleClientSecretGenerator.class);

    private static final Duration LIFETIME = Duration.ofMinutes(5);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final @NotNull Clock clock;

    /** 创建使用指定时钟计算签发时间和过期时间的生成器。 */
    public AppleClientSecretGenerator(@NotNull Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
    }

    /**
     * 使用 Provider 配置里的签名材料签发 {@code client_secret}。
     *
     * @param configuration 含 Client ID、issuer 和已解密签名材料的 Provider 协议配置
     * @return 序列化的 ES256 JWT
     * @throws BusinessException 缺少 Client ID、issuer 或签名材料，签名材料格式无效，或私钥不是 P-256 EC 私钥时抛出
     *                           CONFIGURATION_INVALID，原始异常作为 cause
     */
    public @NotNull String generate(@NotNull FederatedProviderConfiguration configuration) {
        String clientId = configuration.clientId();
        String audience = configuration.issuer();
        String material = configuration.clientSecret();
        if (clientId == null || audience == null || material == null) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID);
        }
        try {
            // 读取签名材料中的 Team ID、Key ID 和私钥；缺少任一字段都不能签发。
            JsonNode json = readMaterial(material);
            String teamId = requiredText(json, "teamId");
            String keyId = requiredText(json, "keyId");
            ECPrivateKey privateKey = privateKey(requiredText(json, "privateKey"));

            // 签发短期 JWT：头部带 Key ID，claims 由 Apple 要求的 iss、sub、aud 和有效期组成。
            Instant now = clock.instant();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(),
                    new JWTClaimsSet.Builder()
                            .issuer(teamId)
                            .subject(clientId)
                            .audience(audience)
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(LIFETIME)))
                            .build());
            jwt.sign(new ECDSASigner(privateKey));
            LOG.trace("Apple client_secret 已签发 | clientId={} ttlSeconds={}", clientId, LIFETIME.toSeconds());
            return jwt.serialize();
        } catch (GeneralSecurityException | JOSEException | IllegalArgumentException exception) {
            throw new BusinessException(FederatedErrorCode.CONFIGURATION_INVALID, exception);
        }
    }

    /** 解析签名材料 JSON；解析失败时不保留 Jackson 异常，因为它的消息会带出未能解析的原文片段，其中可能包含私钥。 */
    private static JsonNode readMaterial(String material) {
        try {
            return MAPPER.readTree(material);
        } catch (JsonProcessingException ignored) {
            throw new IllegalArgumentException("Apple 签名材料不是有效的 JSON");
        }
    }

    /** 读取 JSON 对象中的必填文本字段。 */
    private static String requiredText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Apple 签名材料缺少 " + field);
        }
        return value.asText().strip();
    }

    /** 解析 PKCS#8 PEM 私钥；Apple 的 {@code .p8} 文件使用 P-256 椭圆曲线。 */
    private static ECPrivateKey privateKey(String pem) throws GeneralSecurityException {
        String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        if (!(key instanceof ECPrivateKey ecKey)) {
            throw new IllegalArgumentException("Apple 私钥不是 EC 私钥");
        }
        return ecKey;
    }
}
