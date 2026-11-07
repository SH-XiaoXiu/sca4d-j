package com.sca4d.auth.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.federation.FederatedClaims;
import com.sca4d.auth.federation.FederatedClientAuthentication;
import com.sca4d.auth.federation.FederatedErrorCode;
import com.sca4d.auth.federation.FederatedProfile;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.auth.federation.FederatedProviderProtocol;
import com.sca4d.core.api.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证 Google claims 到联邦资料的转换和 {@code hd} 限制。 */
class GoogleProviderDriverTest {

    private static final String ISSUER = "https://accounts.google.com";

    private final GoogleProviderDriver driver = new GoogleProviderDriver();

    @Test
    @DisplayName("标准 claims 转换为联邦资料，issuer 取自 Provider 配置")
    void mapsClaimsAndTakesIssuerFromConfiguration() {
        FederatedProfile profile = driver.profile(
                claims("""
                        {"sub":"google-sub","name":"Ada","email":"ada@example.com","email_verified":true,
                         "picture":"https://example.com/a.png"}
                        """),
                configuration(Map.of()));

        assertThat(profile.issuer()).isEqualTo(ISSUER);
        assertThat(profile.subject()).isEqualTo("google-sub");
        assertThat(profile.displayName()).isEqualTo("Ada");
        assertThat(profile.email()).isEqualTo("ada@example.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.avatarUrl()).isEqualTo("https://example.com/a.png");
    }

    @Test
    @DisplayName("缺少 sub 时返回资料无效错误码")
    void rejectsMissingSubject() {
        assertThatThrownBy(() -> driver.profile(claims("{\"email\":\"ada@example.com\"}"), configuration(Map.of())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(FederatedErrorCode.PROFILE_INVALID));
    }

    @Test
    @DisplayName("配置了 hd 时，相同域名（忽略大小写）通过，其他域名和个人账号被拒绝")
    void enforcesConfiguredHostedDomain() {
        FederatedProviderConfiguration configured = configuration(Map.of("hd", "Example.com"));

        assertThat(driver.profile(claims("{\"sub\":\"s\",\"hd\":\"example.com\"}"), configured).subject())
                .isEqualTo("s");
        assertDomainRejected(claims("{\"sub\":\"s\",\"hd\":\"other.com\"}"), configured);
        assertDomainRejected(claims("{\"sub\":\"s\"}"), configured);
    }

    @Test
    @DisplayName("hd 配置为 * 时接受任何带 hd 的账号，拒绝没有 hd 的账号")
    void wildcardRequiresAnyHostedDomain() {
        FederatedProviderConfiguration configured = configuration(Map.of("hd", "*"));

        assertThat(driver.profile(claims("{\"sub\":\"s\",\"hd\":\"any.org\"}"), configured).subject())
                .isEqualTo("s");
        assertDomainRejected(claims("{\"sub\":\"s\"}"), configured);
    }

    @Test
    @DisplayName("没有配置 hd 时不检查域名")
    void ignoresHostedDomainWhenNotConfigured() {
        assertThat(driver.profile(claims("{\"sub\":\"s\",\"hd\":\"other.com\"}"), configuration(Map.of())).subject())
                .isEqualTo("s");
    }

    private void assertDomainRejected(FederatedClaims claims, FederatedProviderConfiguration configuration) {
        assertThatThrownBy(() -> driver.profile(claims, configuration))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FederatedErrorCode.PROFILE_DOMAIN_REJECTED));
    }

    private static FederatedClaims claims(String json) {
        try {
            return new FederatedClaims(new ObjectMapper().readTree(json));
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private static FederatedProviderConfiguration configuration(Map<String, String> authorizationParameters) {
        return new FederatedProviderConfiguration(
                FederatedProviderProtocol.OIDC,
                ISSUER,
                "client",
                null,
                null,
                null,
                null,
                null,
                FederatedClientAuthentication.CLIENT_SECRET_POST,
                List.of("openid", "profile", "email"),
                Map.of(),
                authorizationParameters,
                GoogleProviderDriver.CODE);
    }
}
