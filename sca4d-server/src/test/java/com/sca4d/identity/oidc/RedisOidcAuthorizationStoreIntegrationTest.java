package com.sca4d.identity.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 在真实 Redis 7 中验证 OIDC state 按浏览器摘要单次消费。 */
@EnabledIfEnvironmentVariable(named = "SCA4DJ_REDIS_INTEGRATION", matches = "true")
class RedisOidcAuthorizationStoreIntegrationTest {

    @Test
    void shouldPreserveStateForWrongBrowserAndRejectReplayAfterConsumption() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("127.0.0.1", 6377);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        String authorizationId = UUID.randomUUID().toString();
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.getOidc().setAuthorizationKeyPrefix(
                "test:identity:oidc-authorization:" + UUID.randomUUID());
        String key = properties.getOidc().getAuthorizationKeyPrefix() + ":" + authorizationId;
        try {
            RedisOidcAuthorizationStore store = new RedisOidcAuthorizationStore(
                    new RedisScriptExecutor(redis),
                    new ObjectMapper().findAndRegisterModules(),
                    properties);
            OidcAuthorization authorization = new OidcAuthorization()
                    .setAuthorizationId(authorizationId)
                    .setAuthenticationId(UUID.randomUUID().toString())
                    .setProviderId(100L)
                    .setProviderVersion(3L)
                    .setIssuer("https://issuer.example.com")
                    .setTokenUri("https://issuer.example.com/token")
                    .setUserInfoUri("https://issuer.example.com/userinfo")
                    .setJwksUri("https://issuer.example.com/jwks")
                    .setClientId("client-1")
                    .setClientAuthenticationMethod(OidcClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .setScopes(List.of("openid", "profile", "email"))
                    .setNonce("nonce-1")
                    .setCodeVerifier("verifier-1")
                    .setRedirectUri("https://app.example.com/auth/oidc/callback")
                    .setBrowserHash("browser-hash")
                    .setCreatedAt(Instant.now())
                    .setExpiresAt(Instant.now().plusSeconds(30));

            assertThat(store.create(authorization, Duration.ofSeconds(30))).isTrue();
            assertThat(store.create(authorization, Duration.ofSeconds(30))).isFalse();
            assertThat(store.consume(authorizationId, "other-browser")).isNull();
            assertThat(redis.hasKey(key)).isTrue();
            assertThat(store.consume(authorizationId, "browser-hash"))
                    .usingRecursiveComparison()
                    .isEqualTo(authorization);
            assertThat(redis.hasKey(key)).isFalse();
            assertThat(store.consume(authorizationId, "browser-hash")).isNull();
        } finally {
            redis.delete(key);
            connectionFactory.destroy();
        }
    }
}
