package com.sca4d.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.app.server.Sca4dApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = Sca4dApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sca4dj.iam.bootstrap.enabled=true",
        "sca4dj.iam.bootstrap.username=admin",
        "sca4dj.auth.password.bootstrap.enabled=true",
        "sca4dj.auth.password.bootstrap.username=admin",
        "sca4dj.auth.password.bootstrap.password=admin@123",
        "sca4dj.auth.session-ttl=PT1H",
        "sca4dj.auth.token.mode=JWT",
        "sca4dj.auth.jwt.active-key-id=primary",
        "sca4dj.auth.jwt.keys.primary=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sca4dj.auth.jwt.issuer=integration-test",
        "sca4dj.auth.jwt.audience=sca4d-server",
        "sca4dj.auth.jwt.access-ttl=PT5M"
})
@SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection", "DuplicatedCode"})
class JwtSessionLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-alpine")
            .withAdminUser("sca4dj")
            .withAdminPassword("sca4dj");

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "sca4dj");
        registry.add("spring.rabbitmq.password", () -> "sca4dj");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Uses JWT Offline And Requires The Backing Session Token For Online Operations")
    void usesJwtOfflineAndRequiresTheBackingSessionTokenForOnlineOperations() throws Exception {
        JsonNode tokens = login();
        String accessToken = tokens.path("accessToken").asText();
        String sessionToken = tokens.path("sessionToken").asText();
        assertThat(tokens.path("tokenMode").asText()).isEqualTo("JWT");
        assertThat(accessToken.split("\\.")).hasSize(3);
        assertThat(sessionToken).isNotBlank().isNotEqualTo(accessToken);
        assertFiveMinuteLifetime(accessToken);

        assertThat(exchange("/sessions/current", HttpMethod.GET, accessToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange("/sessions/current", HttpMethod.GET, sessionToken).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange("/sessions/current/tokens", HttpMethod.POST, accessToken).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> refreshed = exchange(
                "/sessions/current/tokens",
                HttpMethod.POST,
                sessionToken);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode refreshedTokens = Objects.requireNonNull(refreshed.getBody(), "Token refresh 响应体不能为空")
                .path("data");
        assertThat(refreshedTokens.path("accessToken").asText()).isNotBlank();
        assertThat(refreshedTokens.path("sessionToken").asText()).isEqualTo(sessionToken);
        assertThat(refreshedTokens.path("sessionExpiresAt").asText())
                .isEqualTo(tokens.path("sessionExpiresAt").asText());

        assertThat(exchange("/sessions/current", HttpMethod.DELETE, accessToken).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange("/sessions/current", HttpMethod.DELETE, sessionToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // backing Session 已注销，旧 JWT 在五分钟短窗内仍然只按签名和有效期工作。
        assertThat(exchange("/sessions/current", HttpMethod.GET, accessToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange("/sessions/current/tokens", HttpMethod.POST, sessionToken).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Publishes Separate JWT And Session Bearer Schemes In OpenAPI")
    void publishesSeparateJwtAndSessionBearerSchemesInOpenApi() {
        JsonNode document = http.getForObject("/v3/api-docs", JsonNode.class);

        assertThat(document.path("components").path("securitySchemes").has("jwtBearer")).isTrue();
        assertThat(document.path("components").path("securitySchemes").has("sessionBearer")).isTrue();
        assertThat(
                document.path("paths")
                        .path("/sessions/current")
                        .path("get")
                        .path("security")
                        .get(0)
                        .has("jwtBearer"))
                .isTrue();
        assertThat(
                document.path("paths")
                        .path("/sessions/current/tokens")
                        .path("post")
                        .path("security")
                        .get(0)
                        .has("sessionBearer"))
                .isTrue();
        assertThat(
                document.path("paths")
                        .path("/sessions/current")
                        .path("delete")
                        .path("security")
                        .get(0)
                        .has("sessionBearer"))
                .isTrue();
        assertThat(
                document.path("paths")
                        .path("/platform/accounts/{accountId}")
                        .path("delete")
                        .path("security")
                        .get(0)
                        .has("sessionBearer"))
                .isTrue();
        assertThat(
                document.path("paths")
                        .path("/platform/roles/{roleId}")
                        .path("delete")
                        .path("security")
                        .get(0)
                        .has("sessionBearer"))
                .isTrue();
        assertThat(
                document.path("paths")
                        .path("/me/authentication/totp/credential")
                        .path("delete")
                        .path("security")
                        .get(0)
                        .has("sessionBearer"))
                .isTrue();
    }

    private JsonNode login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/login/attempts",
                new HttpEntity<>(Map.of("identifier", "admin", "password", "admin@123"), headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody(), "登录响应体不能为空")
                .path("data")
                .path("session");
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(headers), JsonNode.class);
    }

    private void assertFiveMinuteLifetime(String accessToken) throws Exception {
        String payload = new String(
                Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]),
                StandardCharsets.UTF_8);
        JsonNode claims = objectMapper.readTree(payload);
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(300);
    }
}
