package com.sca4d.openapi.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.openapi.config.OpenApiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

/** 验证独立 OpenAPI JWT 的签发、恢复和关闭行为。 */
class OpenApiJwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    @DisplayName("签发并恢复独立 OpenAPI JWT")
    void issuesAndRestoresJwt() {
        SessionCodec codec = mock(SessionCodec.class);
        Session session = mock(Session.class);
        Account account = new Account(22, 11, new RealmKey("openapi"));
        when(session.account()).thenReturn(account);
        when(session.id()).thenReturn(new SessionId("session-1"));
        when(session.issuedAt()).thenReturn(NOW.minusSeconds(30));
        when(session.expiresAt()).thenReturn(NOW.plusSeconds(600));
        when(codec.encode(session)).thenReturn("session-wire");
        when(codec.decode("session-wire")).thenReturn(session);

        OpenApiProperties properties = properties();
        OpenApiJwtTokenService service = new OpenApiJwtTokenService(
                codec,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        OpenApiJwtTokenService.IssuedOpenApiJwt issued = service.issue(session);

        assertThat(service.restore(issued.token())).containsSame(session);
        assertThat(service.restore(issued.token() + "x")).isEmpty();
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    @DisplayName("未启用时不接受或签发独立 JWT")
    void staysDisabledByDefault() {
        OpenApiJwtTokenService service = new OpenApiJwtTokenService(
                mock(SessionCodec.class),
                new OpenApiProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.enabled()).isFalse();
        assertThat(service.restore("anything")).isEqualTo(Optional.empty());
    }

    private static OpenApiProperties properties() {
        OpenApiProperties properties = new OpenApiProperties();
        OpenApiProperties.Jwt jwt = properties.getJwt();
        jwt.setEnabled(true);
        jwt.setActiveKeyId("primary");
        jwt.setIssuer("sca4d");
        jwt.setAudience("openapi");
        jwt.getKeys().put("primary", Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }
}
