package com.sca4d.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.sca4d.auth.federation.FederatedClaims;
import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedProfile;
import com.sca4d.core.api.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** 验证 Apple claims 到联邦资料的转换，以及 Driver 签发 client_secret 的接入。 */
class AppleProviderDriverTest {

    private final AppleProviderDriver driver = new AppleProviderDriver(
            new AppleClientSecretGenerator(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)));

    @Test
    @DisplayName("email_verified 为文本 true 时邮箱视为已验证，issuer 取自 Provider 配置，姓名为空")
    void acceptsStringEmailVerified() {
        FederatedProfile profile = driver.profile(
                claims("{\"sub\":\"apple-sub\",\"email\":\"ada@example.com\",\"email_verified\":\"true\"}"),
                AppleTestMaterial.configuration(null));

        assertThat(profile.issuer()).isEqualTo(AppleTestMaterial.ISSUER);
        assertThat(profile.subject()).isEqualTo("apple-sub");
        assertThat(profile.email()).isEqualTo("ada@example.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.displayName()).isNull();
        assertThat(profile.avatarUrl()).isNull();
    }

    @Test
    @DisplayName("隐藏邮箱（文本或布尔 true）保留邮箱但不视为已验证，不能自动关联账号")
    void privateRelayEmailIsNotLinkable() {
        for (String flag : new String[] {"\"true\"", "true"}) {
            FederatedProfile profile = driver.profile(
                    claims(
                            "{\"sub\":\"s\",\"email\":\"x@privaterelay.appleid.com\",\"email_verified\":\"true\","
                                    + "\"is_private_email\":" + flag + "}"),
                    AppleTestMaterial.configuration(null));

            assertThat(profile.email()).isEqualTo("x@privaterelay.appleid.com");
            assertThat(profile.emailVerified()).isFalse();
        }
    }

    @Test
    @DisplayName("is_private_email 为 false 且 email_verified 为 true 时邮箱已验证")
    void realEmailIsLinkable() {
        FederatedProfile profile = driver.profile(
                claims("{\"sub\":\"s\",\"email\":\"a@b.test\",\"email_verified\":true,\"is_private_email\":\"false\"}"),
                AppleTestMaterial.configuration(null));

        assertThat(profile.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("缺少 sub 时返回资料无效错误码")
    void rejectsMissingSubject() {
        assertThatThrownBy(
                () -> driver.profile(claims("{\"email\":\"a@b.test\"}"), AppleTestMaterial.configuration(null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(FederatedErrorCode.PROFILE_INVALID));
    }

    @Test
    @DisplayName("Driver 用 Provider 配置里的签名材料签发 client_secret")
    void signsClientSecretFromConfiguredMaterial() throws Exception {
        String secret = driver.clientSecret(
                AppleTestMaterial.configuration(
                        AppleTestMaterial.material(
                                AppleTestMaterial.TEAM_ID,
                                AppleTestMaterial.KEY_ID,
                                AppleTestMaterial.PRIVATE_KEY_PEM)));

        assertThat(SignedJWT.parse(secret).getHeader().getKeyID()).isEqualTo(AppleTestMaterial.KEY_ID);
    }

    private static FederatedClaims claims(String json) {
        try {
            return new FederatedClaims(new ObjectMapper().readTree(json));
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
