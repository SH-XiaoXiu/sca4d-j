package com.sca4d.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

/** 验证 Apple client_secret 的头部、claims、签名和失败时不泄露签名材料。 */
class AppleClientSecretGeneratorTest {

    private static final String MARKER = "MARKER-SHOULD-NOT-APPEAR";

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("签发的 JWT 头部为 ES256 和 Key ID，claims 符合 Apple 要求，签名可用公钥验证")
    void issuesShortLivedEs256JwtVerifiableWithPublicKey() throws Exception {
        String jwt = generator.generate(configuration(AppleTestMaterial.PRIVATE_KEY_PEM));

        SignedJWT parsed = SignedJWT.parse(jwt);
        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(AppleTestMaterial.KEY_ID);
        assertThat(parsed.getJWTClaimsSet().getIssuer()).isEqualTo(AppleTestMaterial.TEAM_ID);
        assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo(AppleTestMaterial.CLIENT_ID);
        assertThat(parsed.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        Instant issuedAt = parsed.getJWTClaimsSet().getIssueTime().toInstant();
        Instant expiresAt = parsed.getJWTClaimsSet().getExpirationTime().toInstant();
        assertThat(issuedAt).isEqualTo(NOW);
        assertThat(Duration.between(issuedAt, expiresAt)).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5));
        assertThat(parsed.verify(new ECDSAVerifier(publicKey()))).isTrue();
    }

    @Test
    @DisplayName("缺少 Client ID、签名材料字段或私钥无效时返回配置无效")
    void rejectsIncompleteOrInvalidMaterial() {
        List<String> invalid = List.of(
                AppleTestMaterial.material("", AppleTestMaterial.KEY_ID, AppleTestMaterial.PRIVATE_KEY_PEM),
                AppleTestMaterial.material(AppleTestMaterial.TEAM_ID, "", AppleTestMaterial.PRIVATE_KEY_PEM),
                AppleTestMaterial.material(AppleTestMaterial.TEAM_ID, AppleTestMaterial.KEY_ID, "not a key"),
                "{\"teamId\":\"" + AppleTestMaterial.TEAM_ID + "\"}",
                "not json");

        for (String material : invalid) {
            assertThatThrownBy(() -> generator.generate(AppleTestMaterial.configuration(material)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(FederatedErrorCode.CONFIGURATION_INVALID));
        }
        assertThatThrownBy(() -> generator.generate(AppleTestMaterial.configuration(null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("签名材料无法解析时，异常链的消息不包含材料原文")
    void failureMessagesDoNotContainMaterial() {
        List<String> invalid = List.of(
                MARKER,
                AppleTestMaterial.material(
                        AppleTestMaterial.TEAM_ID,
                        AppleTestMaterial.KEY_ID,
                        "-----BEGIN PRIVATE KEY-----" + MARKER + "-----END PRIVATE KEY-----"));

        for (String material : invalid) {
            Throwable failure = catchFailure(material);
            for (Throwable current = failure; current != null; current = current.getCause()) {
                assertThat(String.valueOf(current.getMessage())).doesNotContain(MARKER);
            }
        }
    }

    @Test
    @DisplayName("签发过程在 TRACE 级别也不记录私钥和 JWT")
    void doesNotLogKeyOrJwt() {
        Logger logger = (Logger) LoggerFactory.getLogger(AppleClientSecretGenerator.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);
        try {
            String jwt = generator.generate(configuration(AppleTestMaterial.PRIVATE_KEY_PEM));

            assertThat(appender.list).isNotEmpty();
            for (ILoggingEvent event : appender.list) {
                assertThat(event.getFormattedMessage())
                        .doesNotContain(AppleTestMaterial.PRIVATE_KEY_BODY.substring(0, 32))
                        .doesNotContain(jwt);
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    private Throwable catchFailure(String material) {
        try {
            generator.generate(AppleTestMaterial.configuration(material));
        } catch (BusinessException exception) {
            return exception;
        }
        throw new AssertionError("签名材料应当被拒绝");
    }

    private static com.sca4d.auth.federation.FederatedProviderConfiguration configuration(String privateKey) {
        return AppleTestMaterial.configuration(
                AppleTestMaterial.material(AppleTestMaterial.TEAM_ID, AppleTestMaterial.KEY_ID, privateKey));
    }

    private static ECPublicKey publicKey() throws Exception {
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(AppleTestMaterial.PUBLIC_KEY_BODY)));
    }
}
