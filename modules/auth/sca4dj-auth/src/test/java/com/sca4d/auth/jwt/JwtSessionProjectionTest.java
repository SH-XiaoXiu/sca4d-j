package com.sca4d.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.jwt.JWT;
import com.sca4d.auth.config.AuthProperties;
import com.sca4d.auth.config.AuthSettings;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.auth.testing.SessionTestFixture;
import com.sca4d.core.api.authentication.AuthTokenMode;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionTokens;
import com.sca4d.core.api.setting.Settings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;

class JwtSessionProjectionTest {

    private static final String OLD_KEY = key("old-key-material-for-hs256-signing");

    private static final String NEW_KEY = key("new-key-material-for-hs256-signing");

    private static final String LEGACY_JWT = "eyJ0eXAiOiJzZXNzaW9uK2p3dCIsImtpZCI6Im9sZCIsImFsZyI6IkhTMjU2In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImF1ZCI6InRlc3QtYXVkaWVuY2UiLCJzdWIiOiIyMiIsImlhdCI6MTc4NDY3ODQwMCwiZXhwIjoxNzg0Njc4NzAwLCJzaWQiOiJzZXNzaW9uLTEiLCJzZXNzaW9uIjoie1wiZm9ybWF0VmVyc2lvblwiOjEsXCJpZFwiOlwic2Vzc2lvbi0xXCIsXCJzdWJqZWN0XCI6e1wic3ViamVjdElkXCI6MTEsXCJ0eXBlXCI6XCJIVU1BTlwifSxcImFjY291bnRcIjp7XCJhY2NvdW50SWRcIjoyMixcInN1YmplY3RJZFwiOjExLFwicmVhbG1LZXlcIjpcInRlc3RcIn0sXCJwcmluY2lwYWxzXCI6W3tcInR5cGVcIjpcInRlc3QucHJpbmNpcGFsXCIsXCJwYXlsb2FkXCI6XCJleUpwWkNJNk16TXNJbVpwWld4a0lqb2ljSEpwYm1OcGNHRnNMV1pwWld4a0luMD1cIn1dLFwiYXV0aGVudGljYXRpb25zXCI6W3tcImFjY291bnRJZFwiOjIyLFwibWV0aG9kQ29kZVwiOlwicGFzc3dvcmRcIixcImZhY3RvcktpbmRzXCI6W1wiS05PV0xFREdFXCIsXCJQT1NTRVNTSU9OXCJdLFwiYXV0aGVudGljYXRlZEF0XCI6XCIyMDI2LTA3LTIyVDAwOjAwOjAwWlwifV0sXCJyZXNvdXJjZXNcIjpbe1wicHJpbmNpcGFsSWRcIjozMyxcInJlc291cmNlXCI6e1widHlwZVwiOlwidGVzdC5yZXNvdXJjZVwiLFwicGF5bG9hZFwiOlwiZXlKMGVYQmxJam9pYjNCbGNtRjBhVzl1SWl3aWFXUWlPaUp3YkdGMFptOXliUzVoWTJOdmRXNTBMbkpsWVdRaUxDSm1hV1ZzWkNJNkluSmxjMjkxY21ObExXWnBaV3hrSW4wPVwifX1dLFwiaXNzdWVkQXRcIjpcIjIwMjYtMDctMjJUMDA6MDA6MDBaXCIsXCJleHBpcmVzQXRcIjpcIjIwMjYtMDctMjJUMDE6MDA6MDBaXCJ9In0.ojQqe9A3SpzAMGrnU9tVRUwphVN9nee5YTg003chcsE";

    @Test
    @DisplayName("Restores A Fixed Legacy JWT")
    void restoresAFixedLegacyJwt() {
        JwtSessionProjection projection = new JwtSessionProjection(
                SessionTestFixture.codec(),
                jwtProperties("old", OLD_KEY),
                settings(),
                Clock.fixed(SessionTestFixture.NOW.plusSeconds(60), ZoneOffset.UTC));

        Session restored = projection.restore(LEGACY_JWT).orElseThrow();

        assertThat(restored.id().value()).isEqualTo("session-1");
        assertThat(restored.account().accountId()).isEqualTo(22L);
    }

    @Test
    @DisplayName("Does Not Require JWT Identity Or Keys In Session Mode")
    void doesNotRequireJwtIdentityOrKeysInSessionMode() {
        JwtSessionProjection projection = new JwtSessionProjection(
                SessionTestFixture.codec(),
                new AuthProperties(),
                settings(),
                Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC));

        assertThat(projection.mode()).isEqualTo(AuthTokenMode.SESSION);
    }

    @Test
    @DisplayName("Requires Issuer And Audience In JWT Mode")
    void requiresIssuerAndAudienceInJwtMode() {
        AuthProperties properties = new AuthProperties();
        properties.setTokenMode(AuthTokenMode.JWT);
        properties.getJwt().setActiveKeyId("primary");
        properties.getJwt().getKeys().put("primary", NEW_KEY);

        assertThatThrownBy(
                () -> new JwtSessionProjection(
                        SessionTestFixture.codec(),
                        properties,
                        settings(),
                        Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    @DisplayName("Signs Five Minute JWT Without Loading The Backing Session")
    void signsAndRestoresAFiveMinuteSessionProjectionWithoutReadingTheBackingSession() {
        SessionCodec codec = SessionTestFixture.codec();
        AuthProperties properties = jwtProperties("old", OLD_KEY);
        JwtSessionProjection projection = new JwtSessionProjection(
                codec,
                properties,
                settings(),
                Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC));
        Session session = SessionTestFixture.session(SessionTestFixture.NOW.plusSeconds(3600));

        SessionTokens tokens = projection.issue(session, "opaque-session-token");
        Session restored = projection.restore(tokens.accessToken()).orElseThrow();

        assertThat(tokens.tokenMode()).isEqualTo(AuthTokenMode.JWT);
        assertThat(tokens.accessTokenExpiresAt()).isEqualTo(SessionTestFixture.NOW.plusSeconds(300));
        assertThat(tokens.sessionToken()).isEqualTo("opaque-session-token");
        assertThat(JWT.of(tokens.accessToken()).getHeader("typ")).isEqualTo("session+jwt");
        assertThat(JWT.of(tokens.accessToken()).getHeader("kid")).isEqualTo("old");
        assertThat(restored.id()).isEqualTo(session.id());
        assertThat(restored.account().accountId()).isEqualTo(session.account().accountId());
        assertThat(restored.account().subjectId()).isEqualTo(session.account().subjectId());
        assertThat(restored.account().realmKey()).isEqualTo(session.account().realmKey());
    }

    @Test
    @DisplayName("Keeps Ten Thousand Sequential Principal IDs Within The Default JWT Limit")
    void keepsTenThousandSequentialPrincipalIdsWithinTheDefaultJwtLimit() {
        JwtSessionProjection projection = new JwtSessionProjection(
                SessionTestFixture.codec(),
                jwtProperties("old", OLD_KEY),
                settings(),
                Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC));
        Session session = SessionTestFixture.sessionWithCompactIds(
                SessionTestFixture.NOW.plusSeconds(3600),
                10_000);

        SessionTokens tokens = projection.issue(session, "opaque-session-token");
        Session restored = projection.restore(tokens.accessToken()).orElseThrow();

        assertThat(tokens.accessToken()).hasSizeLessThanOrEqualTo(6144);
        assertThat(restored.principalIdSets()).singleElement()
                .extracting(principalIds -> principalIds.ids().size())
                .isEqualTo(10_000);
    }

    @Test
    @DisplayName("Accepts Historical Verification Keys But Signs With The Active Key")
    void acceptsHistoricalVerificationKeysButSignsWithTheActiveKey() {
        SessionCodec codec = SessionTestFixture.codec();
        Session session = SessionTestFixture.session(SessionTestFixture.NOW.plusSeconds(3600));
        JwtSessionProjection oldProjection = new JwtSessionProjection(
                codec,
                jwtProperties("old", OLD_KEY),
                settings(),
                Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC));
        String oldToken = oldProjection.issue(session, "opaque").accessToken();

        AuthProperties rotated = jwtProperties("new", NEW_KEY);
        rotated.getJwt().getKeys().put("old", OLD_KEY);
        JwtSessionProjection newProjection = new JwtSessionProjection(
                codec,
                rotated,
                settings(),
                Clock.fixed(SessionTestFixture.NOW.plusSeconds(60), ZoneOffset.UTC));
        String newToken = newProjection.issue(session, "opaque").accessToken();

        assertThat(newProjection.restore(oldToken)).isPresent();
        assertThat(JWT.of(newToken).getHeader("kid")).isEqualTo("new");
    }

    @Test
    @DisplayName("Rejects Expired Tokens Wrong Signatures And Short Keys")
    void rejectsExpiredTokensWrongSignaturesAndShortKeys() {
        SessionCodec codec = SessionTestFixture.codec();
        Session session = SessionTestFixture.session(SessionTestFixture.NOW.plusSeconds(3600));
        JwtSessionProjection projection = new JwtSessionProjection(
                codec,
                jwtProperties("old", OLD_KEY),
                settings(),
                Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC));
        String token = projection.issue(session, "opaque").accessToken();

        JwtSessionProjection expired = new JwtSessionProjection(
                codec,
                jwtProperties("old", OLD_KEY),
                settings(),
                Clock.fixed(SessionTestFixture.NOW.plusSeconds(301), ZoneOffset.UTC));
        JwtSessionProjection wrongKey = new JwtSessionProjection(
                codec,
                jwtProperties("old", NEW_KEY),
                settings(),
                Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC));
        AuthProperties shortKey = jwtProperties(
                "short",
                Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8)));

        assertThat(expired.restore(token)).isEmpty();
        assertThat(wrongKey.restore(token)).isEmpty();
        assertThatThrownBy(
                () -> new JwtSessionProjection(
                        codec,
                        shortKey,
                        settings(),
                        Clock.fixed(SessionTestFixture.NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能少于 32 字节");
    }

    private static AuthProperties jwtProperties(String activeKeyId, String key) {
        AuthProperties properties = new AuthProperties();
        properties.setTokenMode(AuthTokenMode.JWT);
        properties.getJwt().setActiveKeyId(activeKeyId);
        properties.getJwt().getKeys().put(activeKeyId, key);
        properties.getJwt().setIssuer("test-issuer");
        properties.getJwt().setAudience("test-audience");
        return properties;
    }

    /** @return 固定返回五分钟 JWT 有效期的 Settings */
    private static Settings settings() {
        Settings settings = mock(Settings.class);
        when(settings.get(AuthSettings.JWT_ACCESS_TTL)).thenReturn(Duration.ofMinutes(5));
        return settings;
    }

    private static String key(String seed) {
        byte[] source = seed.getBytes(StandardCharsets.UTF_8);
        byte[] key = Arrays.copyOf(source, 32);
        return Base64.getEncoder().encodeToString(key);
    }
}
