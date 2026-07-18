package com.sca4d.identity.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.redis.RedisScriptDefinition;
import com.sca4d.common.redis.RedisScriptExecutor;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 验证 OIDC 授权请求只通过资源 Lua 创建并按浏览器摘要单次消费。 */
class RedisOidcAuthorizationStoreTest {

    private final RedisScriptExecutor scripts = mock(RedisScriptExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
    private final RedisOidcAuthorizationStore store = new RedisOidcAuthorizationStore(
            scripts, objectMapper, properties);

    @Test
    void shouldCreateAuthorizationWithMillisecondTtl() {
        when(scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                eq(List.of("sca4dj:identity:oidc-authorization:state-1")),
                anyString(),
                eq("300000")))
                .thenReturn(1L);

        assertThat(store.create(authorization(), Duration.ofMinutes(5))).isTrue();

        verify(scripts).execute(
                ArgumentMatchers.<RedisScriptDefinition<Long>>any(),
                eq(List.of("sca4dj:identity:oidc-authorization:state-1")),
                anyString(),
                eq("300000"));
    }

    @Test
    void shouldConsumeAuthorizationOnlyWithBrowserHash() throws Exception {
        OidcAuthorization authorization = authorization();
        when(scripts.execute(
                ArgumentMatchers.<RedisScriptDefinition<String>>any(),
                eq(List.of("sca4dj:identity:oidc-authorization:state-1")),
                eq("browser-hash")))
                .thenReturn(objectMapper.writeValueAsString(authorization));

        assertThat(store.consume("state-1", "browser-hash"))
                .usingRecursiveComparison()
                .isEqualTo(authorization);
        assertThat(store.consume("state-1", "other-browser")).isNull();
    }

    /** @return 包含 state、浏览器摘要和过期时间的测试授权请求 */
    private OidcAuthorization authorization() {
        return new OidcAuthorization()
                .setAuthorizationId("state-1")
                .setAuthenticationId("flow-1")
                .setBrowserHash("browser-hash")
                .setCreatedAt(Instant.parse("2026-07-17T00:00:00Z"))
                .setExpiresAt(Instant.parse("2026-07-17T00:05:00Z"));
    }
}
