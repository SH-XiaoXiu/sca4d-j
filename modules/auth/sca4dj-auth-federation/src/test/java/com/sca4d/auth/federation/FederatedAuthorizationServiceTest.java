package com.sca4d.auth.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.federation.provider.FederatedProviderRepository;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 验证授权回调按 Provider 配置选择 Driver，并保留未配置 Driver 时的原资料映射。 */
class FederatedAuthorizationServiceTest {

    private static final String BINDING = "browser-binding";

    private static final URI REDIRECT_URI = URI.create("https://app.example.test/callback");

    private final Map<String, FederatedAuthorization> authorizations = new HashMap<>();

    private final List<FederatedProfile> completedProfiles = new ArrayList<>();

    private MockRestServiceServer server;

    private RestClient restClient;

    @BeforeEach
    void createRestClient() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @Test
    @DisplayName("配置了 Driver 的 Provider 使用 Driver 转换 UserInfo，并把 Provider 配置传给 Driver")
    void usesConfiguredDriverForProfile() {
        RecordingDriver driver = new RecordingDriver();
        FederatedAuthorizationService service = service(configuration(TreeCode.of("TEST")), List.of(driver));
        expectTokenAndUserInfo();

        service.callback(started(service), "code", null, BINDING, REDIRECT_URI);

        assertThat(completedProfiles).singleElement().satisfies(profile -> {
            assertThat(profile.subject()).isEqualTo("driver-u-1");
            assertThat(profile.issuer()).isNull();
        });
        assertThat(driver.seenId).isEqualTo("u-1");
        assertThat(driver.seenClientId).isEqualTo("client");
        server.verify();
    }

    @Test
    @DisplayName("没有配置 Driver 的 Provider 继续使用 profileMapping")
    void keepsProfileMappingWithoutDriver() {
        FederatedAuthorizationService service = service(configuration(null), List.of(new RecordingDriver()));
        expectTokenAndUserInfo();

        service.callback(started(service), "code", null, BINDING, REDIRECT_URI);

        assertThat(completedProfiles).singleElement().satisfies(profile -> {
            assertThat(profile.subject()).isEqualTo("u-1");
            assertThat(profile.displayName()).isEqualTo("ada");
        });
    }

    @Test
    @DisplayName("签名凭据认证方式由 Driver 签发 client_secret，且不发送已保存的签名材料")
    void sendsClientSecretSignedByDriver() {
        FederatedAuthorizationService service = service(
                configuration(TreeCode.of("TEST"), FederatedClientAuthentication.CLIENT_SECRET_SIGNED_JWT),
                List.of(new RecordingDriver()));
        server.expect(requestTo("https://idp.example.test/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=signed-by-driver")))
                .andExpect(
                        content().string(
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret=secret"))))
                .andRespond(withSuccess("{\"access_token\":\"access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://idp.example.test/userinfo"))
                .andRespond(withSuccess("{\"id\":\"u-1\"}", MediaType.APPLICATION_JSON));

        service.callback(started(service), "code", null, BINDING, REDIRECT_URI);

        server.verify();
    }

    @Test
    @DisplayName("签名凭据认证方式没有选择 Driver 时授权码交换返回配置无效")
    void rejectsSignedSecretWithoutDriver() {
        FederatedAuthorizationService service = service(
                configuration(null, FederatedClientAuthentication.CLIENT_SECRET_SIGNED_JWT),
                List.of());
        String state = started(service);

        assertThatThrownBy(() -> service.callback(state, "code", null, BINDING, REDIRECT_URI))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FederatedErrorCode.CONFIGURATION_INVALID));
    }

    @Test
    @DisplayName("Provider 配置的 Driver 没有登记时在授权开始前返回配置无效")
    void rejectsUnregisteredDriverBeforeAuthorization() {
        FederatedAuthorizationService service = service(configuration(TreeCode.of("MISSING")), List.of());

        assertThatThrownBy(
                () -> service.start("TEST", FederatedAuthorizationPurpose.LOGIN, BINDING, REDIRECT_URI))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FederatedErrorCode.CONFIGURATION_INVALID));
        assertThat(authorizations).isEmpty();
    }

    private void expectTokenAndUserInfo() {
        server.expect(requestTo("https://idp.example.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=secret")))
                .andRespond(withSuccess("{\"access_token\":\"access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://idp.example.test/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access"))
                .andRespond(withSuccess("{\"id\":\"u-1\",\"login\":\"ada\"}", MediaType.APPLICATION_JSON));
    }

    /** 开始授权并返回保存的 state。 */
    private String started(FederatedAuthorizationService service) {
        service.start("TEST", FederatedAuthorizationPurpose.LOGIN, BINDING, REDIRECT_URI);
        return authorizations.keySet().iterator().next();
    }

    private FederatedAuthorizationService service(
            FederatedProviderConfiguration configuration,
            List<FederatedProviderDriver> drivers) {
        FederatedProviderDriverRegistry registry = new FederatedProviderDriverRegistry(drivers);
        FederatedProvider provider = new FederatedProvider(
                1,
                "TEST",
                FederatedProviderProtocol.OAUTH2,
                FederatedProviderStatus.ACTIVE,
                true,
                0,
                FederatedUnlinkedIdentityAction.REJECT,
                false,
                Map.of(),
                null,
                new Version(0));
        FederatedProviderService providers = new FederatedProviderService(
                new FixedProviderRepository(provider, configuration),
                registry,
                new LocaleCatalog(List.of(Locale.SIMPLIFIED_CHINESE), Locale.SIMPLIFIED_CHINESE));
        return new FederatedAuthorizationService(
                providers,
                registry,
                new MemoryStore(),
                new ObjectMapper(),
                restClient,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static FederatedProviderConfiguration configuration(TreeCode driver) {
        return configuration(driver, FederatedClientAuthentication.CLIENT_SECRET_POST);
    }

    private static FederatedProviderConfiguration configuration(
            TreeCode driver,
            FederatedClientAuthentication authentication) {
        return new FederatedProviderConfiguration(
                FederatedProviderProtocol.OAUTH2,
                null,
                "client",
                "secret",
                "https://idp.example.test/authorize",
                "https://idp.example.test/token",
                "https://idp.example.test/userinfo",
                null,
                authentication,
                List.of("profile"),
                Map.of("subject", "$.id", "displayName", "$.login"),
                Map.of(),
                driver);
    }

    /** 保存收到的 claims 和配置，并返回带前缀的 subject。 */
    private static class RecordingDriver implements FederatedProviderDriver {

        private String seenId;

        private String seenClientId;

        @Override
        public @NotNull TreeCode code() {
            return TreeCode.of("TEST");
        }

        @Override
        public @NotNull FederatedProfile profile(
                @NotNull FederatedClaims claims,
                @NotNull FederatedProviderConfiguration configuration) {
            seenId = claims.text("id");
            seenClientId = configuration.clientId();
            return new FederatedProfile(configuration.issuer(), "driver-" + seenId, null, null, false, null);
        }

        @Override
        public @NotNull String clientSecret(@NotNull FederatedProviderConfiguration configuration) {
            return "signed-by-driver";
        }
    }

    /** 只提供一个固定 Provider 及其协议配置。 */
    private static class FixedProviderRepository implements FederatedProviderRepository {

        private final FederatedProvider provider;

        private final FederatedProviderConfiguration configuration;

        private FixedProviderRepository(FederatedProvider provider, FederatedProviderConfiguration configuration) {
            this.provider = provider;
            this.configuration = configuration;
        }

        @Override
        public @NotNull Optional<FederatedProvider> findByCode(@NotNull String code) {
            return provider.code().equals(code) ? Optional.of(provider) : Optional.empty();
        }

        @Override
        public @NotNull FederatedProviderConfiguration configuration(@NotNull FederatedProvider ignored) {
            return configuration;
        }

        @Override
        public @NotNull FederatedProviderConfiguration authorizationConfiguration(@NotNull FederatedProvider ignored) {
            return configuration;
        }

        @Override
        public @NotNull List<FederatedProvider> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull List<FederatedProvider> findLoginProviders() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull FederatedProvider insert(@NotNull FederatedProvider ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull FederatedProvider replace(
                @NotNull String code,
                boolean loginPageVisible,
                int displayOrder,
                @NotNull FederatedUnlinkedIdentityAction action,
                boolean autoLinkVerifiedEmail,
                @NotNull Map<String, Map<String, String>> i18n,
                @Nullable String iconReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull FederatedProvider changeStatus(@NotNull String code, @NotNull FederatedProviderStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(@NotNull String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull FederatedProvider saveConfiguration(
                @NotNull String code,
                @NotNull FederatedProviderConfiguration ignored) {
            throw new UnsupportedOperationException();
        }
    }

    /** 在内存中保存授权状态，并记录完成的资料。 */
    private class MemoryStore implements FederatedAuthorizationStore {

        @Override
        public void save(@NotNull FederatedAuthorization authorization, @NotNull Duration ttl) {
            authorizations.put(authorization.id(), authorization);
        }

        @Override
        public @Nullable FederatedAuthorization consume(@NotNull String id, @NotNull String browserBinding) {
            return authorizations.remove(id);
        }

        @Override
        public void saveCompletion(
                @NotNull String id,
                @NotNull String providerCode,
                @NotNull FederatedAuthorizationPurpose purpose,
                @NotNull FederatedProfile profile,
                @NotNull String browserBinding,
                @NotNull Duration ttl) {
            completedProfiles.add(profile);
        }

        @Override
        public @Nullable FederatedCompletedAuthorization consumeCompletion(
                @NotNull String id,
                @NotNull String browserBinding,
                @NotNull FederatedAuthorizationPurpose purpose) {
            throw new UnsupportedOperationException();
        }
    }
}
