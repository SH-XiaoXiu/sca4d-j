package com.sca4d.auth.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sca4d.auth.federation.provider.FederatedProviderRepository;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 验证协议配置校验中与 Driver 有关的规则。 */
class FederatedProviderServiceTest {

    private final FederatedProviderDriver registered = new FederatedProviderDriver() {
        @Override
        public TreeCode code() {
            return TreeCode.of("TEST");
        }

        @Override
        public FederatedProfile profile(FederatedClaims claims, FederatedProviderConfiguration configuration) {
            return new FederatedProfile(null, claims.subject(), null, null, false, null);
        }
    };

    private final FederatedProviderService service = new FederatedProviderService(
            mock(FederatedProviderRepository.class),
            new FederatedProviderDriverRegistry(List.of(registered)),
            new LocaleCatalog(List.of(Locale.SIMPLIFIED_CHINESE), Locale.SIMPLIFIED_CHINESE));

    @Test
    @DisplayName("配置选择未登记的 Driver 时校验失败")
    void rejectsUnregisteredDriver() {
        assertThatThrownBy(() -> service.validate(oauth2(TreeCode.of("MISSING"), Map.of("subject", "$.id"))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FederatedErrorCode.CONFIGURATION_INVALID));
    }

    @Test
    @DisplayName("OAuth2 配置选择已登记的 Driver 时不再要求 subject 映射")
    void acceptsDriverWithoutSubjectMapping() {
        assertThatCode(() -> service.validate(oauth2(TreeCode.of("TEST"), Map.of()))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OAuth2 配置没有 Driver 且缺少 subject 映射时校验失败")
    void requiresSubjectMappingWithoutDriver() {
        assertThatThrownBy(() -> service.validate(oauth2(null, Map.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("签名凭据认证方式没有选择 Driver 时校验失败")
    void requiresDriverForSignedClientSecret() {
        assertThatThrownBy(
                () -> service.validate(
                        oauth2(
                                null,
                                Map.of("subject", "$.id"),
                                FederatedClientAuthentication.CLIENT_SECRET_SIGNED_JWT)))
                .isInstanceOf(BusinessException.class);
        assertThatCode(
                () -> service.validate(
                        oauth2(
                                TreeCode.of("TEST"),
                                Map.of(),
                                FederatedClientAuthentication.CLIENT_SECRET_SIGNED_JWT)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("授权参数允许 response_mode，保留参数仍被拒绝")
    void allowsResponseModeButNotReservedParameters() {
        assertThatCode(() -> service.validate(withParameters(Map.of("response_mode", "form_post"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validate(withParameters(Map.of("state", "fixed"))))
                .isInstanceOf(BusinessException.class);
    }

    private static FederatedProviderConfiguration withParameters(Map<String, String> parameters) {
        return new FederatedProviderConfiguration(
                FederatedProviderProtocol.OIDC,
                "https://idp.example.test",
                "client",
                null,
                null,
                null,
                null,
                null,
                FederatedClientAuthentication.CLIENT_SECRET_POST,
                List.of("openid"),
                Map.of(),
                parameters,
                null);
    }

    private static FederatedProviderConfiguration oauth2(TreeCode driver, Map<String, String> mapping) {
        return oauth2(driver, mapping, FederatedClientAuthentication.CLIENT_SECRET_POST);
    }

    private static FederatedProviderConfiguration oauth2(
            TreeCode driver,
            Map<String, String> mapping,
            FederatedClientAuthentication authentication) {
        return new FederatedProviderConfiguration(
                FederatedProviderProtocol.OAUTH2,
                null,
                "client",
                null,
                "https://idp.example.test/authorize",
                "https://idp.example.test/token",
                "https://idp.example.test/userinfo",
                null,
                authentication,
                List.of(),
                mapping,
                Map.of(),
                driver);
    }
}
