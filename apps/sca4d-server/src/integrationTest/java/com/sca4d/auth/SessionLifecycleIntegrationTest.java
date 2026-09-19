package com.sca4d.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.sca4d.app.server.Sca4dApplication;
import com.sca4d.app.server.authentication.AuthenticationHttpContract;
import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.password.PasswordAuthenticationMethod;
import com.sca4d.auth.session.RedisSessionGeneration;
import com.sca4d.auth.session.SaTokenSessionManager;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.rbac.PrincipalRoles;
import com.sca4d.iam.rbac.Role;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = Sca4dApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sca4dj.iam.bootstrap.enabled=true",
        "sca4dj.iam.bootstrap.username=admin",
        "sca4dj.auth.password.bootstrap-enabled=true",
        "sca4dj.auth.password.bootstrap-username=admin",
        "sca4dj.auth.password.bootstrap-password=admin@123",
        "sca4dj.auth.session-ttl=PT1H"
})
@SuppressWarnings({
        "SqlNoDataSourceInspection",
        "SqlResolve",
        "SpringJavaInjectionPointsAutowiringInspection",
        "DuplicatedCode"
})
class SessionLifecycleIntegrationTest {

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
    private PlatformIdentityService identities;

    @Autowired
    private AccountDirectory accountDirectory;

    @Autowired
    private PasswordAuthenticationMethod passwords;

    @Autowired
    private PlatformRbacService rbac;

    @Autowired
    private OperationCatalog operations;

    @Autowired
    private SaTokenSessionManager sessions;

    @Autowired
    private RedisSessionGeneration generations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Owns Every Business Route In The Example App")
    void ownsEveryBusinessRouteInTheExampleApp() {
        Set<String> businessRoots = Set.of(
                "/login",
                "/verifications",
                "/sessions",
                "/me",
                "/system",
                "/platform",
                "/files",
                "/events");
        var businessHandlers = handlerMapping.getHandlerMethods()
                .entrySet()
                .stream()
                .filter(
                        entry -> entry.getKey()
                                .getPatternValues()
                                .stream()
                                .anyMatch(path -> businessRoots.stream().anyMatch(path::startsWith)))
                .toList();

        assertThat(businessHandlers)
                .allSatisfy(
                        entry -> assertThat(entry.getValue().getBeanType().getPackageName())
                                .startsWith("com.sca4d.app.server"));
        assertThat(
                businessHandlers.stream()
                        .flatMap(entry -> entry.getKey().getPatternValues().stream())
                        .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        "/login/attempts",
                        "/login/attempts/passkey",
                        "/login/attempts/federated",
                        "/login/email-code/challenges",
                        "/login/email-code/proofs",
                        "/login/sms-code/challenges",
                        "/login/sms-code/proofs",
                        "/login/federated-providers",
                        "/login/federated-providers/{providerCode}/authorizations",
                        "/login/federated-authorizations/callback",
                        "/login/attempts/{attemptId}/password/proofs",
                        "/login/attempts/{attemptId}/totp/proofs",
                        "/login/attempts/{attemptId}/passkey/proofs",
                        "/login/attempts/{attemptId}/email-code/challenges",
                        "/login/attempts/{attemptId}/email-code/proofs",
                        "/login/attempts/{attemptId}/sms-code/challenges",
                        "/login/attempts/{attemptId}/sms-code/proofs",
                        "/login/attempts/{attemptId}/federated/proofs",
                        "/verifications",
                        "/verifications/{verificationId}/password/proofs",
                        "/verifications/{verificationId}/totp/proofs",
                        "/verifications/{verificationId}/passkey/proofs",
                        "/verifications/{verificationId}/email-code/challenges",
                        "/verifications/{verificationId}/email-code/proofs",
                        "/verifications/{verificationId}/sms-code/challenges",
                        "/verifications/{verificationId}/sms-code/proofs",
                        "/verifications/{verificationId}/federated/proofs",
                        "/sessions/current",
                        "/sessions/current/tokens",
                        "/me/profile",
                        "/me/profile/avatar",
                        "/me/preferences/{key}",
                        "/me/notification-settings",
                        "/me/notification-settings/language",
                        "/me/notification-settings/region",
                        "/me/notification-settings/channels",
                        "/me/notifications",
                        "/me/notifications/{messageId}/read",
                        "/me/notifications/read-all",
                        "/me/phone-identifier",
                        "/me/password",
                        "/me/authentication/settings",
                        "/me/authentication/totp/credential",
                        "/me/authentication/totp/enrollments",
                        "/me/authentication/totp/enrollments/{enrollmentId}",
                        "/me/authentication/totp/enrollments/{enrollmentId}/qr-code",
                        "/me/passkeys",
                        "/me/passkeys/{credentialId}",
                        "/me/passkeys/registrations/options",
                        "/me/authentication/federated-identities",
                        "/me/authentication/federated-identities/{identityId}",
                        "/me/authentication/federated-identities/{providerCode}/authorizations",
                        "/system/locales",
                        "/system/permissions",
                        "/system/audit/records",
                        "/system/audit/records/{id}",
                        "/system/dictionaries",
                        "/system/dictionaries/{key}",
                        "/system/dictionaries/{key}/options",
                        "/system/dictionaries/{key}/options/{value}",
                        "/system/settings",
                        "/system/settings/{key}",
                        "/system/email/senders",
                        "/system/email/senders/{senderId}",
                        "/system/email/senders/{senderId}/status",
                        "/system/email/senders/{senderId}/connection-tests",
                        "/system/email/templates",
                        "/system/email/templates/{templateId}",
                        "/system/email/templates/{templateId}/status",
                        "/system/email/templates/previews",
                        "/system/sms/providers",
                        "/system/sms/providers/{providerId}",
                        "/system/sms/providers/{providerId}/status",
                        "/system/sms/providers/{providerId}/bindings",
                        "/system/sms/providers/{providerId}/bindings/{bindingId}",
                        "/system/sms/providers/{providerId}/bindings/{bindingId}/status",
                        "/system/authentication/federated-providers",
                        "/system/authentication/federated-providers/{code}",
                        "/system/authentication/federated-providers/{code}/settings",
                        "/system/authentication/federated-providers/{code}/status",
                        "/platform/accounts",
                        "/platform/accounts/{accountId}",
                        "/platform/accounts/{accountId}/roles",
                        "/platform/accounts/{accountId}/profile/avatar",
                        "/platform/roles",
                        "/platform/roles/{roleId}",
                        "/platform/roles/{roleId}/i18n",
                        "/platform/roles/{roleId}/permissions",
                        "/platform/roles/{roleId}/status",
                        "/files/uploads",
                        "/files/uploads/{fileId}/completion",
                        "/files/access-urls",
                        "/files/transfers/uploads",
                        "/files/transfers/objects",
                        "/events",
                        "/events/ws/tickets");
    }

    @Test
    @DisplayName("Bootstraps Logs In Restores The Redis Backed Session And Logs Out")
    void bootstrapsLogsInRestoresTheRedisBackedSessionAndLogsOut() throws Exception {
        JsonNode establishment = login("admin", "admin@123");
        String tokenValue = establishment.path("accessToken").asText();
        String sessionId = establishment.path("sessionId").asText();
        assertThat(establishment.path("tokenMode").asText()).isEqualTo("SESSION");
        assertThat(establishment.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(establishment.path("sessionToken").isNull()).isTrue();
        assertThat(tokenValue).isNotBlank();
        assertThat(sessionId).isNotBlank();

        HttpHeaders authenticatedHeaders = authenticatedHeaders(tokenValue);
        ResponseEntity<JsonNode> firstRead = http.exchange(
                "/sessions/current",
                HttpMethod.GET,
                new HttpEntity<>(authenticatedHeaders),
                JsonNode.class);
        ResponseEntity<JsonNode> secondRead = http.exchange(
                "/sessions/current",
                HttpMethod.GET,
                new HttpEntity<>(authenticatedHeaders),
                JsonNode.class);

        assertThat(firstRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode firstBody = Objects.requireNonNull(firstRead.getBody());
        JsonNode secondBody = Objects.requireNonNull(secondRead.getBody());
        assertThat(firstBody.path("data").path("sessionId").asText()).isEqualTo(sessionId);
        assertThat(secondBody.path("data").path("sessionId").asText()).isEqualTo(sessionId);
        JsonNode subjectId = firstBody.path("data").path("subject").path("subjectId");
        JsonNode accountId = firstBody.path("data").path("account").path("accountId");
        assertThat(subjectId.isTextual()).isTrue();
        assertThat(subjectId.asLong()).isPositive();
        assertThat(accountId.isTextual()).isTrue();
        assertThat(accountId.asLong()).isPositive();
        assertThat(firstBody.path("data").has("principals")).isFalse();
        assertThat(firstBody.path("data").path("permissions")).isNotEmpty();
        assertThat(
                REDIS.execInContainer(
                        "redis-cli",
                        "--scan",
                        "--pattern",
                        "*token-session*" + tokenValue)
                        .getStdout())
                .isNotBlank();

        ResponseEntity<JsonNode> roles = http.exchange(
                "/platform/roles",
                HttpMethod.GET,
                new HttpEntity<>(authenticatedHeaders),
                JsonNode.class);
        ResponseEntity<JsonNode> invalidAccountId = http.exchange(
                "/platform/accounts/0",
                HttpMethod.GET,
                new HttpEntity<>(authenticatedHeaders),
                JsonNode.class);
        ResponseEntity<JsonNode> openApi = http.getForEntity("/v3/api-docs", JsonNode.class);

        assertThat(roles.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roles.getBody()).isNotNull();
        assertThat(Objects.requireNonNull(roles.getBody()).path("data").findValuesAsText("code"))
                .contains("ROOT", "DEFAULT");
        assertThat(roles.getBody().path("data").findValues("id"))
                .allSatisfy(id -> assertThat(id.isTextual()).isTrue());
        assertThat(roles.getBody().path("data").findValues("version")).isEmpty();
        assertThat(invalidAccountId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(invalidAccountId.getBody()).path("code").asText()).isEqualTo("1100");
        assertThat(invalidAccountId.getBody().path("data").findValuesAsText("field"))
                .containsExactly("accountId");
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode openApiBody = Objects.requireNonNull(openApi.getBody());
        JsonNode paths = openApiBody.path("paths");
        assertThat(paths.has("/sessions/current")).isTrue();
        assertThat(paths.has("/login/attempts")).isTrue();
        assertThat(paths.has("/login/attempts/{attemptId}/totp/proofs")).isTrue();
        assertThat(paths.has("/verifications")).isTrue();
        assertThat(paths.has("/verifications/{verificationId}/password/proofs")).isTrue();
        assertThat(paths.has("/verifications/{verificationId}/totp/proofs")).isTrue();
        assertThat(paths.has("/me/profile")).isTrue();
        assertThat(paths.has("/me/password")).isTrue();
        assertThat(paths.has("/me/authentication/settings")).isTrue();
        assertThat(paths.has("/me/authentication/totp/credential")).isTrue();
        assertThat(paths.has("/me/authentication/totp/enrollments/{enrollmentId}/qr-code")).isTrue();
        assertThat(paths.has("/me/passkeys")).isTrue();
        assertThat(paths.has("/system/locales")).isTrue();
        assertThat(paths.has("/system/permissions")).isTrue();
        assertThat(paths.has("/platform/accounts")).isTrue();
        assertThat(paths.has("/platform/accounts/{accountId}/roles")).isTrue();
        assertThat(paths.has("/platform/roles")).isTrue();
        assertThat(paths.has("/platform/roles/{roleId}/i18n")).isTrue();
        assertThat(paths.has("/platform/roles/{roleId}/permissions")).isTrue();
        assertThat(paths.has("/platform/roles/{roleId}/status")).isTrue();
        assertThat(paths.has("/events")).isTrue();
        assertThat(paths.has("/callbacks/{code}")).isFalse();
        assertThat(paths.has("/session/current")).isFalse();
        assertThat(paths.has("/authentications/login/password-proofs")).isFalse();
        assertThat(paths.has("/verifications/{attemptId}/password/proofs")).isFalse();
        assertThat(paths.has("/verifications/{attemptId}/totp/proofs")).isFalse();
        assertThat(paths.has("/platform/principals/{principalId}/roles")).isFalse();
        assertThat(paths.has("/system/operations")).isFalse();
        assertThat(paths.has("/realtime/sse")).isFalse();
        JsonNode schemas = openApiBody.path("components").path("schemas");
        assertThat(schemas.path("PlatformRole").path("properties").path("id").path("type").asText())
                .isEqualTo("string");
        assertThat(schemas.path("PlatformRole").path("properties").has("version")).isFalse();
        assertThat(
                schemas.path("ReplaceAccountRolesRequest")
                        .path("properties")
                        .path("roleIds")
                        .path("items")
                        .path("type")
                        .asText())
                .isEqualTo("string");
        assertThat(
                schemas.path("ReplaceAccountRolesRequest")
                        .path("properties")
                        .path("roleIds")
                        .path("maxItems")
                        .asInt())
                .as(
                        "roleIds schema=%s",
                        schemas.path("ReplaceAccountRolesRequest")
                                .path("properties")
                                .path("roleIds"))
                .isEqualTo(1000);
        assertThat(
                schemas.path("ReplaceAccountRolesRequest")
                        .path("properties")
                        .path("roleIds")
                        .path("items")
                        .path("pattern")
                        .asText())
                .isEqualTo("^[1-9][0-9]*$");
        assertThat(
                schemas.path("CreateFileAccessUrlsRequest")
                        .path("properties")
                        .path("fileIds")
                        .path("maxItems")
                        .asInt())
                .isEqualTo(100);
        assertThat(schemas.path("PasswordLoginRequest").path("properties").has("identifier")).isTrue();
        assertThat(schemas.path("PasswordLoginRequest").path("properties").has("username")).isFalse();
        assertThat(openApiBody.toString()).doesNotContain("x-sca4dj-http-input");

        ResponseEntity<JsonNode> logout = http.exchange(
                "/sessions/current",
                HttpMethod.DELETE,
                new HttpEntity<>(authenticatedHeaders),
                JsonNode.class);
        ResponseEntity<JsonNode> afterLogout = http.exchange(
                "/sessions/current",
                HttpMethod.GET,
                new HttpEntity<>(authenticatedHeaders),
                JsonNode.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(afterLogout.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(
                REDIS.execInContainer(
                        "redis-cli",
                        "--scan",
                        "--pattern",
                        "*token-session*" + tokenValue)
                        .getStdout())
                .isBlank();
    }

    @Test
    @DisplayName("Manages Account Profile Roles Password And Session Invalidation Through HTTP")
    void managesAccountProfileRolesPasswordAndSessionInvalidationThroughHttp() {
        String username = "managed-flow-user";
        String initialPassword = "managed@123";
        String replacementPassword = "managed@456";
        HttpHeaders adminHeaders = authenticatedHeaders(login("admin", "admin@123").path("accessToken").asText());
        adminHeaders.setAcceptLanguageAsLocales(List.of(Locale.forLanguageTag("zh-CN")));

        ResponseEntity<JsonNode> createdAccountResponse = http.postForEntity(
                "/platform/accounts",
                new HttpEntity<>(
                        Map.of(
                                "username",
                                username,
                                "initialPassword",
                                initialPassword,
                                "displayName",
                                "Managed Flow",
                                "email",
                                "managed@example.com",
                                "phoneNumber",
                                "+8613800138000"),
                        adminHeaders),
                JsonNode.class);
        assertThat(createdAccountResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdAccountResponse.getHeaders().getLocation()).isNotNull();
        assertThat(createdAccountResponse.getHeaders().getETag()).isNull();
        JsonNode createdAccount = Objects.requireNonNull(createdAccountResponse.getBody()).path("data");
        assertThat(createdAccount.path("id").isTextual()).isTrue();
        assertThat(createdAccount.has("version")).isFalse();
        long accountId = Long.parseLong(createdAccount.path("id").asText());
        assertThat(accountId).isPositive();
        assertThat(createdAccountResponse.getHeaders().getLocation().getPath())
                .isEqualTo("/platform/accounts/" + accountId);
        assertThat(createdAccount.path("username").asText()).isEqualTo(username);
        assertThat(createdAccount.path("displayName").asText()).isEqualTo("Managed Flow");
        assertThat(
                accountDirectory.resolve(
                        PlatformIdentityService.PLATFORM_REALM,
                        EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE,
                        "MANAGED@example.com"))
                .map(Account::accountId)
                .contains(accountId);

        Long subjectCountBeforeDuplicateEmail = jdbc.queryForObject("SELECT COUNT(*) FROM iam_subject", Long.class);
        ResponseEntity<JsonNode> duplicateEmail = http.postForEntity(
                "/platform/accounts",
                new HttpEntity<>(
                        Map.of(
                                "username",
                                "managed-flow-email-duplicate",
                                "initialPassword",
                                initialPassword,
                                "displayName",
                                "Duplicate Email",
                                "email",
                                "MANAGED@example.com"),
                        adminHeaders),
                JsonNode.class);
        assertThat(duplicateEmail.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_subject", Long.class))
                .isEqualTo(subjectCountBeforeDuplicateEmail);
        assertThat(
                accountDirectory.resolve(
                        PlatformIdentityService.PLATFORM_REALM,
                        AccountIdentifierType.USERNAME,
                        "managed-flow-email-duplicate"))
                .isEmpty();

        ResponseEntity<JsonNode> duplicate = http.postForEntity(
                "/platform/accounts",
                new HttpEntity<>(
                        Map.of(
                                "username",
                                username,
                                "initialPassword",
                                initialPassword,
                                "displayName",
                                "Duplicate"),
                        adminHeaders),
                JsonNode.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> accountPage = http.exchange(
                "/platform/accounts?page=1&size=10&search=managed-flow&status=ACTIVE",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                JsonNode.class);
        assertThat(accountPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = Objects.requireNonNull(accountPage.getBody()).path("data");
        assertThat(page.path("page").asInt()).isEqualTo(1);
        assertThat(page.path("size").asInt()).isEqualTo(10);
        assertThat(page.path("total").isTextual()).isTrue();
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(page.path("items")).hasSize(1);

        ResponseEntity<JsonNode> accountRolesResponse = http.exchange(
                "/platform/accounts/" + accountId + "/roles",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                JsonNode.class);
        assertThat(accountRolesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accountRolesResponse.getHeaders().getETag()).isNotBlank();
        JsonNode accountRoles = Objects.requireNonNull(accountRolesResponse.getBody()).path("data");
        assertThat(accountRoles.path("accountId").isTextual()).isTrue();
        assertThat(accountRoles.has("version")).isFalse();
        assertThat(accountRoles.has("roles")).isFalse();
        assertThat(textValues(accountRoles.path("roleIds"))).hasSize(1);
        long defaultRoleId = Long.parseLong(accountRoles.path("roleIds").get(0).asText());

        ResponseEntity<JsonNode> createdRoleResponse = http.postForEntity(
                "/platform/roles",
                new HttpEntity<>(
                        Map.of(
                                "code",
                                "MANAGED_FLOW_ROLE",
                                "i18n",
                                Map.of(
                                        "zh-CN",
                                        Map.of("name", "受管流程角色"),
                                        "en-US",
                                        Map.of("name", "Managed Flow Role")),
                                "permissionCodes",
                                List.of("platform:accounts:read")),
                        adminHeaders),
                JsonNode.class);
        assertThat(createdRoleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdRoleResponse.getHeaders().getLocation()).isNotNull();
        assertThat(createdRoleResponse.getHeaders().getETag()).isEqualTo("\"0\"");
        JsonNode createdRole = Objects.requireNonNull(createdRoleResponse.getBody()).path("data");
        assertThat(createdRole.path("id").isTextual()).isTrue();
        assertThat(createdRole.has("version")).isFalse();
        assertThat(createdRole.has("i18n")).isFalse();
        assertThat(createdRole.path("name").asText()).isEqualTo("受管流程角色");
        long roleId = Long.parseLong(createdRole.path("id").asText());
        assertThat(createdRoleResponse.getHeaders().getLocation().getPath())
                .isEqualTo("/platform/roles/" + roleId);

        ResponseEntity<JsonNode> roleI18nResponse = http.exchange(
                "/platform/roles/" + roleId + "/i18n",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                JsonNode.class);
        assertThat(roleI18nResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleI18nResponse.getHeaders().getETag()).isEqualTo("\"0\"");
        JsonNode roleI18n = Objects.requireNonNull(roleI18nResponse.getBody()).path("data");
        assertThat(roleI18n.path("roleId").asLong()).isEqualTo(roleId);
        assertThat(roleI18n.path("i18n").path("zh-CN").path("name").asText())
                .isEqualTo("受管流程角色");
        assertThat(roleI18n.path("i18n").path("en-US").path("name").asText())
                .isEqualTo("Managed Flow Role");

        ResponseEntity<JsonNode> rolePermissionsResponse = http.exchange(
                "/platform/roles/" + roleId + "/permissions",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                JsonNode.class);
        assertThat(rolePermissionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                textValues(
                        Objects.requireNonNull(rolePermissionsResponse.getBody())
                                .path("data")
                                .path("permissionCodes")))
                .containsExactlyInAnyOrder("platform:accounts:read");
        ResponseEntity<JsonNode> missingPermissionsVersion = http.exchange(
                "/platform/roles/" + roleId + "/permissions",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("permissionCodes", List.of("platform:accounts:manage")), adminHeaders),
                JsonNode.class);
        assertThat(missingPermissionsVersion.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        HttpHeaders replacePermissionsHeaders = copyHeaders(adminHeaders);
        replacePermissionsHeaders.setIfMatch(
                Objects.requireNonNull(rolePermissionsResponse.getHeaders().getETag()));
        ResponseEntity<JsonNode> replacedPermissions = http.exchange(
                "/platform/roles/" + roleId + "/permissions",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "permissionCodes",
                                List.of("platform:accounts:read", "platform:accounts:manage")),
                        replacePermissionsHeaders),
                JsonNode.class);
        assertThat(replacedPermissions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replacedPermissions.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(
                textValues(
                        Objects.requireNonNull(replacedPermissions.getBody())
                                .path("data")
                                .path("permissionCodes")))
                .containsExactlyInAnyOrder("platform:accounts:read", "platform:accounts:manage");
        ResponseEntity<JsonNode> stalePermissions = http.exchange(
                "/platform/roles/" + roleId + "/permissions",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of("permissionCodes", List.of("platform:accounts:read")),
                        replacePermissionsHeaders),
                JsonNode.class);
        assertThat(stalePermissions.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);

        HttpHeaders staleI18nHeaders = copyHeaders(adminHeaders);
        staleI18nHeaders.setIfMatch(Objects.requireNonNull(roleI18nResponse.getHeaders().getETag()));
        ResponseEntity<JsonNode> staleI18n = http.exchange(
                "/platform/roles/" + roleId + "/i18n",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "i18n",
                                Map.of("zh-CN", Map.of("name", "过期名称"))),
                        staleI18nHeaders),
                JsonNode.class);
        assertThat(staleI18n.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);

        HttpHeaders replaceI18nHeaders = copyHeaders(adminHeaders);
        replaceI18nHeaders.setIfMatch(Objects.requireNonNull(replacedPermissions.getHeaders().getETag()));
        ResponseEntity<JsonNode> replacedI18n = http.exchange(
                "/platform/roles/" + roleId + "/i18n",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "i18n",
                                Map.of(
                                        "zh-CN",
                                        Map.of("name", "已更新角色"),
                                        "en-US",
                                        Map.of("name", "Updated Role"))),
                        replaceI18nHeaders),
                JsonNode.class);
        assertThat(replacedI18n.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replacedI18n.getHeaders().getETag()).isEqualTo("\"2\"");
        assertThat(
                Objects.requireNonNull(replacedI18n.getBody())
                        .path("data")
                        .path("i18n")
                        .path("en-US")
                        .path("name")
                        .asText())
                .isEqualTo("Updated Role");

        HttpHeaders englishHeaders = copyHeaders(adminHeaders);
        englishHeaders.setAcceptLanguageAsLocales(List.of(Locale.forLanguageTag("en-US")));
        ResponseEntity<JsonNode> englishRole = http.exchange(
                "/platform/roles/" + roleId,
                HttpMethod.GET,
                new HttpEntity<>(englishHeaders),
                JsonNode.class);
        assertThat(englishRole.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(englishRole.getHeaders().getETag()).isEqualTo("\"2\"");
        assertThat(Objects.requireNonNull(englishRole.getBody()).path("data").path("name").asText())
                .isEqualTo("Updated Role");

        ResponseEntity<JsonNode> missingStatusVersion = http.exchange(
                "/platform/roles/" + roleId + "/status",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "DISABLED"), adminHeaders),
                JsonNode.class);
        assertThat(missingStatusVersion.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        HttpHeaders updateStatusHeaders = copyHeaders(adminHeaders);
        updateStatusHeaders.setIfMatch(Objects.requireNonNull(replacedI18n.getHeaders().getETag()));
        ResponseEntity<JsonNode> updatedStatus = http.exchange(
                "/platform/roles/" + roleId + "/status",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "DISABLED"), updateStatusHeaders),
                JsonNode.class);
        assertThat(updatedStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedStatus.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(Objects.requireNonNull(updatedStatus.getBody()).path("data").path("status").asText())
                .isEqualTo("DISABLED");

        ResponseEntity<JsonNode> missingRolesVersion = http.exchange(
                "/platform/accounts/" + accountId + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("roleIds", List.of(Long.toString(roleId))), adminHeaders),
                JsonNode.class);
        assertThat(missingRolesVersion.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        HttpHeaders replaceRolesHeaders = copyHeaders(adminHeaders);
        replaceRolesHeaders.setIfMatch(
                Objects.requireNonNull(accountRolesResponse.getHeaders().getETag()));
        ResponseEntity<JsonNode> replacedRoles = http.exchange(
                "/platform/accounts/" + accountId + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "roleIds",
                                List.of(Long.toString(defaultRoleId), Long.toString(roleId))),
                        replaceRolesHeaders),
                JsonNode.class);
        assertThat(replacedRoles.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(textValues(Objects.requireNonNull(replacedRoles.getBody()).path("data").path("roleIds")))
                .containsExactlyInAnyOrder(Long.toString(defaultRoleId), Long.toString(roleId));
        ResponseEntity<JsonNode> staleRoles = http.exchange(
                "/platform/accounts/" + accountId + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("roleIds", List.of(Long.toString(defaultRoleId))), replaceRolesHeaders),
                JsonNode.class);
        assertThat(staleRoles.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);

        JsonNode managedSession = login("managed@example.com", initialPassword);
        String managedToken = managedSession.path("accessToken").asText();
        HttpHeaders managedHeaders = authenticatedHeaders(managedToken);
        ResponseEntity<JsonNode> profileResponse = http.exchange(
                "/me/profile",
                HttpMethod.GET,
                new HttpEntity<>(managedHeaders),
                JsonNode.class);
        assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profileResponse.getHeaders().getETag()).isNull();
        assertThat(Objects.requireNonNull(profileResponse.getBody()).path("data").has("version")).isFalse();

        ResponseEntity<JsonNode> replacedProfile = http.exchange(
                "/me/profile",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "displayName",
                                "Updated Profile",
                                "email",
                                "updated@example.com",
                                "phoneNumber",
                                "+8613900139000"),
                        managedHeaders),
                JsonNode.class);
        assertThat(replacedProfile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replacedProfile.getHeaders().getETag()).isNull();
        assertThat(Objects.requireNonNull(replacedProfile.getBody()).path("data").path("displayName").asText())
                .isEqualTo("Updated Profile");
        assertThat(
                accountDirectory.resolve(
                        PlatformIdentityService.PLATFORM_REALM,
                        EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE,
                        "managed@example.com"))
                .isEmpty();
        assertThat(
                accountDirectory.resolve(
                        PlatformIdentityService.PLATFORM_REALM,
                        EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE,
                        "UPDATED@example.com"))
                .map(Account::accountId)
                .contains(accountId);

        ResponseEntity<JsonNode> passwordResponse = http.exchange(
                "/me/password",
                HttpMethod.GET,
                new HttpEntity<>(managedHeaders),
                JsonNode.class);
        assertThat(passwordResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordResponse.getHeaders().getETag()).isNull();
        JsonNode passwordView = Objects.requireNonNull(passwordResponse.getBody()).path("data");
        assertThat(passwordView.path("status").asText()).isEqualTo("SET");
        assertThat(passwordView.has("version")).isFalse();
        assertThat(passwordView.has("passwordHash")).isFalse();

        String passwordVerificationId = startSatisfiedVerification(
                managedHeaders,
                "authentication.password.replace");
        HttpHeaders replacePasswordHeaders = copyHeaders(managedHeaders);
        replacePasswordHeaders.set(AuthenticationHttpContract.VERIFICATION_HEADER, passwordVerificationId);
        ResponseEntity<JsonNode> replacedPassword = http.exchange(
                "/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("password", replacementPassword), replacePasswordHeaders),
                JsonNode.class);
        assertThat(replacedPassword.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(
                http.exchange(
                        "/sessions/current",
                        HttpMethod.GET,
                        new HttpEntity<>(managedHeaders),
                        JsonNode.class)
                        .getStatusCode()
                        .is2xxSuccessful())
                .isFalse();
        assertThat(loginFailure(username, initialPassword).is2xxSuccessful()).isFalse();

        JsonNode replacementSession = login(username, replacementPassword);
        HttpHeaders replacementHeaders = authenticatedHeaders(replacementSession.path("accessToken").asText());
        ResponseEntity<JsonNode> adminSession = http.exchange(
                "/sessions/current",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                JsonNode.class);
        long adminAccountId = Objects.requireNonNull(adminSession.getBody())
                .path("data")
                .path("account")
                .path("accountId")
                .asLong();
        ResponseEntity<JsonNode> selfDisable = http.exchange(
                "/platform/accounts/" + adminAccountId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "DISABLED"), adminHeaders),
                JsonNode.class);
        assertThat(selfDisable.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> disabled = http.exchange(
                "/platform/accounts/" + accountId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "DISABLED"), adminHeaders),
                JsonNode.class);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getHeaders().getETag()).isNull();
        assertThat(
                http.exchange(
                        "/sessions/current",
                        HttpMethod.GET,
                        new HttpEntity<>(replacementHeaders),
                        JsonNode.class)
                        .getStatusCode()
                        .is2xxSuccessful())
                .isFalse();
        assertThat(loginFailure(username, replacementPassword).is2xxSuccessful()).isFalse();
    }

    @Test
    @DisplayName("Refreshes Redis Backed Session Resources After Role Changes")
    void refreshesRedisBackedSessionResourcesAfterRoleChanges() {
        PlatformPrincipal principal = identities.provision("refresh-user");
        Account account = identities.findActiveAccounts(principal.principalId()).getFirst();
        passwords.bootstrap("refresh-user", "refresh@123");
        rbac.bindDefaultRole(principal.principalId());

        String tokenValue = login("refresh-user", "refresh@123").path("accessToken").asText();
        Session initial = sessions.current(tokenValue).orElseThrow();
        assertThat(initial.resources(principal.principalId())).isEmpty();
        long establishedGeneration = generations.current(account.accountId());

        String operationCode = operations.actions().getFirst().operation().value();
        Role refreshRole = rbac.createRole(
                "SESSION_REFRESH_TEST",
                LocalizedFields.of(
                        java.util.Map.of(
                                java.util.Locale.forLanguageTag("zh-CN"),
                                java.util.Map.of("name", "Session Refresh Test"))),
                java.util.List.of(operationCode))
                .role();
        PrincipalRoles currentRoles = rbac.findPrincipalRoles(principal.principalId());
        PrincipalRoles replacedRoles = rbac.replacePrincipalRoles(
                principal.principalId(),
                java.util.stream.Stream.concat(
                        currentRoles.roles().stream().map(Role::roleId),
                        java.util.stream.Stream.of(refreshRole.roleId()))
                        .toList());
        assertThat(replacedRoles.roles()).extracting(Role::roleId).contains(refreshRole.roleId());

        assertThat(generations.current(account.accountId())).isEqualTo(establishedGeneration + 1);
        Session refreshed = sessions.current(tokenValue).orElseThrow();
        assertThat(refreshed.resources(principal.principalId()))
                .filteredOn(Operation.class::isInstance)
                .map(Operation.class::cast)
                .extracting(Operation::value)
                .contains(operationCode);
        assertThat(refreshed.id()).isEqualTo(initial.id());
    }

    @Test
    @DisplayName("Enrolls Totp And Uses It For Two Factor Login")
    void enrollsTotpAndUsesItForTwoFactorLogin() throws Exception {
        PlatformPrincipal principal = identities.provision("totp-user");
        passwords.bootstrap("totp-user", "totp@123");
        rbac.bindDefaultRole(principal.principalId());

        JsonNode firstSession = login("totp-user", "totp@123");
        String firstToken = firstSession.path("accessToken").asText();
        HttpHeaders firstHeaders = authenticatedHeaders(firstToken);

        String enrollmentStepUp = startSatisfiedVerification(
                firstHeaders,
                "authentication.totp.enroll");

        HttpHeaders enrollmentHeaders = new HttpHeaders(firstHeaders);
        enrollmentHeaders.set(AuthenticationHttpContract.VERIFICATION_HEADER, enrollmentStepUp);
        JsonNode enrollment = postEnrollment(enrollmentHeaders);
        String enrollmentId = enrollment.path("enrollmentId").asText();
        assertThat(enrollmentId).isNotBlank();

        ResponseEntity<byte[]> qrResponse = http.exchange(
                "/me/authentication/totp/enrollments/" + enrollmentId + "/qr-code",
                HttpMethod.GET,
                new HttpEntity<>(firstHeaders),
                byte[].class);
        assertThat(qrResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(qrResponse.getHeaders().getCacheControl()).contains("no-store");
        byte[] png = qrResponse.getBody();
        assertThat(png).isNotNull().startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        String provisioningUri = decodeQr(png);
        assertThat(provisioningUri).startsWith("otpauth://totp/Sca4d-J:account-");
        String secret = querySecret(provisioningUri);

        String activationCode = totpCode(secret, Instant.now().minusSeconds(10));
        JsonNode credential = createTotpCredential(
                Map.of("enrollmentId", enrollmentId, "code", activationCode),
                firstHeaders);
        assertThat(credential.path("enabled").asBoolean()).isTrue();
        assertThat(credential.has("version")).isFalse();
        assertThat(
                http.exchange(
                        "/sessions/current",
                        HttpMethod.GET,
                        new HttpEntity<>(firstHeaders),
                        JsonNode.class)
                        .getStatusCode()
                        .is2xxSuccessful())
                .isTrue();

        JsonNode policySession = login("totp-user", "totp@123");
        HttpHeaders policyHeaders = authenticatedHeaders(policySession.path("accessToken").asText());
        String policyStepUp = startSatisfiedVerification(
                policyHeaders,
                "authentication.settings.replace");
        HttpHeaders replacePolicyHeaders = new HttpHeaders(policyHeaders);
        replacePolicyHeaders.set(AuthenticationHttpContract.VERIFICATION_HEADER, policyStepUp);
        JsonNode policy = replaceAuthenticationSettings(
                Map.of(
                        "loginFactorCount",
                        2,
                        "sensitiveActionFactorCount",
                        1,
                        "reusableAuthenticationMaximumAgeSeconds",
                        600,
                        "methods",
                        defaultAuthenticationMethods()),
                replacePolicyHeaders);
        assertThat(policy.path("loginFactorCount").asInt()).isEqualTo(2);

        JsonNode pendingLogin = startLogin("totp-user", "totp@123");
        assertThat(pendingLogin.path("authentication").path("status").asText()).isEqualTo("FACTOR_REQUIRED");
        assertThat(pendingLogin.path("session").isNull()).isTrue();
        assertThat(pendingLogin.path("authentication").path("availableMethods").toString()).contains("\"totp\"");
        String loginAttempt = pendingLogin.path("authentication").path("attemptId").asText();
        String loginCode = totpCode(secret, Instant.now().plusSeconds(30));
        JsonNode completedLogin = postJson(
                "/login/attempts/" + loginAttempt + "/totp/proofs",
                Map.of("code", loginCode),
                jsonHeaders());
        assertThat(completedLogin.path("authentication").path("status").asText()).isEqualTo("SATISFIED");
        assertThat(completedLogin.path("session").path("accessToken").asText()).isNotBlank();

        JsonNode replayAttempt = startLogin("totp-user", "totp@123");
        ResponseEntity<JsonNode> replay = http.postForEntity(
                "/login/attempts/"
                        + replayAttempt.path("authentication").path("attemptId").asText()
                        + "/totp/proofs",
                new HttpEntity<>(Map.of("code", loginCode), jsonHeaders()),
                JsonNode.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                Objects.requireNonNull(replay.getBody(), "TOTP 登录响应体不能为空")
                        .path("data")
                        .path("authentication")
                        .path("status")
                        .asText())
                .isEqualTo("SATISFIED");
        assertThat(
                REDIS.execInContainer(
                        "redis-cli",
                        "--scan",
                        "--pattern",
                        "sca4d-server:sca4dj:auth:totp-enrollment:*")
                        .getStdout())
                .isNotBlank();
    }

    @Test
    @DisplayName("Deletes Account Only With Totp And Removes Its Security And Personal Data")
    void deletesAccountOnlyWithTotpAndRemovesItsSecurityAndPersonalData() throws Exception {
        HttpHeaders adminHeaders = authenticatedHeaders(login("admin", "admin@123").path("accessToken").asText());
        String adminTotpSecret = enrollTotp(adminHeaders);

        String username = "deleted-account-user";
        String password = "deleted@123";
        ResponseEntity<JsonNode> created = http.postForEntity(
                "/platform/accounts",
                new HttpEntity<>(
                        Map.of(
                                "username",
                                username,
                                "initialPassword",
                                password,
                                "displayName",
                                "Deleted Account",
                                "email",
                                "deleted@example.com",
                                "phoneNumber",
                                "+8613800138111"),
                        adminHeaders),
                JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode account = Objects.requireNonNull(created.getBody()).path("data");
        long accountId = Long.parseLong(account.path("id").asText());
        long subjectId = Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT subject_id FROM iam_account WHERE id = ?",
                        Long.class,
                        accountId));
        long principalId = Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT principal_id FROM iam_account_principal WHERE account_id = ?",
                        Long.class,
                        accountId));

        JsonNode targetSession = login(username, password);
        String targetToken = targetSession.path("accessToken").asText();
        HttpHeaders targetHeaders = authenticatedHeaders(targetToken);
        enrollTotp(targetHeaders);
        jdbc.update(
                "INSERT INTO platform_acl_entry (principal_id, resource_type, resource_id, action) "
                        + "VALUES (?, 'document', 'deletion-test', 'read')",
                principalId);

        String settingsVerification = startSatisfiedVerification(
                adminHeaders,
                "authentication.settings.replace");
        HttpHeaders settingsHeaders = copyHeaders(adminHeaders);
        settingsHeaders.set(AuthenticationHttpContract.VERIFICATION_HEADER, settingsVerification);
        replaceAuthenticationSettings(
                Map.of(
                        "loginFactorCount",
                        1,
                        "sensitiveActionFactorCount",
                        2,
                        "reusableAuthenticationMaximumAgeSeconds",
                        600,
                        "methods",
                        defaultAuthenticationMethods()),
                settingsHeaders);

        String deleteVerification = startRequiredAccountDeletionVerification(adminHeaders);
        JsonNode totpVerification = postJson(
                "/verifications/" + deleteVerification + "/totp/proofs",
                Map.of("code", totpCode(adminTotpSecret, Instant.now().plusSeconds(30))),
                adminHeaders);
        assertThat(totpVerification.path("status").asText()).isEqualTo("SATISFIED");
        HttpHeaders deleteHeaders = copyHeaders(adminHeaders);
        deleteHeaders.set(AuthenticationHttpContract.VERIFICATION_HEADER, deleteVerification);
        ResponseEntity<JsonNode> deleted = http.exchange(
                "/platform/accounts/" + accountId,
                HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders),
                JsonNode.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(
                http.exchange(
                        "/platform/accounts/" + accountId,
                        HttpMethod.GET,
                        new HttpEntity<>(adminHeaders),
                        JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                http.exchange(
                        "/sessions/current",
                        HttpMethod.GET,
                        new HttpEntity<>(targetHeaders),
                        JsonNode.class).getStatusCode().is2xxSuccessful())
                .isFalse();
        assertThat(loginFailure(username, password).is2xxSuccessful()).isFalse();
        assertThat(
                jdbc.queryForObject(
                        "SELECT status FROM iam_account WHERE id = ?",
                        String.class,
                        accountId))
                .isEqualTo("DISABLED");
        assertThat(
                jdbc.queryForObject(
                        "SELECT deleted FROM iam_account WHERE id = ?",
                        Integer.class,
                        accountId))
                .isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                        "SELECT status FROM iam_principal WHERE id = ?",
                        String.class,
                        principalId))
                .isEqualTo("DISABLED");
        assertThat(
                jdbc.queryForList(
                        "SELECT identifier_type, value, status, deleted FROM iam_account_identifier "
                                + "WHERE account_id = ? ORDER BY identifier_type",
                        accountId))
                .containsExactly(
                        Map.of(
                                "identifier_type",
                                "EMAIL",
                                "value",
                                "deleted@example.com",
                                "status",
                                "DISABLED",
                                "deleted",
                                1),
                        Map.of(
                                "identifier_type",
                                "USERNAME",
                                "value",
                                username,
                                "status",
                                "DISABLED",
                                "deleted",
                                1));
        assertThat(
                jdbc.queryForMap(
                        "SELECT status, deleted FROM iam_subject WHERE id = ?",
                        subjectId))
                .containsEntry("status", "DISABLED")
                .containsEntry("deleted", 1);
        assertThat(
                jdbc.queryForObject(
                        "SELECT display_name FROM platform_user_profile WHERE principal_id = ?",
                        String.class,
                        principalId))
                .isEqualTo("已删除账号");
        assertThat(
                jdbc.queryForMap(
                        "SELECT email, phone_number, deleted FROM platform_user_profile WHERE principal_id = ?",
                        principalId))
                .containsEntry("email", null)
                .containsEntry("phone_number", null)
                .containsEntry("deleted", 1);
        assertThat(
                jdbc.queryForMap(
                        "SELECT status, deleted FROM auth_password_credential WHERE account_id = ?",
                        accountId))
                .containsEntry("status", "DISABLED")
                .containsEntry("deleted", 1);
        assertThat(
                jdbc.queryForMap(
                        "SELECT status, deleted FROM auth_totp_credential WHERE account_id = ?",
                        accountId))
                .containsEntry("status", "DISABLED")
                .containsEntry("deleted", 1);
        assertThat(
                count(
                        "SELECT COUNT(*) FROM auth_password_secret secret INNER JOIN auth_password_credential credential "
                                + "ON credential.id = secret.credential_id WHERE credential.account_id = ?",
                        accountId))
                .isZero();
        assertThat(
                count(
                        "SELECT COUNT(*) FROM auth_totp_secret secret INNER JOIN auth_totp_credential credential "
                                + "ON credential.id = secret.credential_id WHERE credential.account_id = ?",
                        accountId))
                .isZero();
        assertThat(
                count(
                        "SELECT COUNT(*) FROM sys_setting "
                                + "WHERE owner_type = 'account' AND owner_id = CAST(? AS TEXT) "
                                + "AND setting_key = 'authentication.account-policy'",
                        accountId))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM iam_account_principal WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM platform_principal_role WHERE principal_id = ?", principalId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM platform_acl_entry WHERE principal_id = ?", principalId)).isZero();

        ResponseEntity<JsonNode> reusedUsername = http.postForEntity(
                "/platform/accounts",
                new HttpEntity<>(
                        Map.of(
                                "username",
                                username,
                                "initialPassword",
                                "reused@123",
                                "displayName",
                                "Reused Username"),
                        adminHeaders),
                JsonNode.class);
        assertThat(reusedUsername.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode login(String identifier, String password) {
        JsonNode outcome = startLogin(identifier, password);
        assertThat(outcome.path("authentication").path("status").asText()).isEqualTo("SATISFIED");
        return outcome.path("session");
    }

    private JsonNode startLogin(String identifier, String password) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/login/attempts",
                new HttpEntity<>(Map.of("identifier", identifier, "password", password), jsonHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).startsWith("/login/attempts/");
        assertThat(response.getBody()).isNotNull();
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    private HttpStatusCode loginFailure(String identifier, String password) {
        return http.postForEntity(
                "/login/attempts",
                new HttpEntity<>(Map.of("identifier", identifier, "password", password), jsonHeaders()),
                JsonNode.class)
                .getStatusCode();
    }

    private static List<String> textValues(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private static HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders copy = new HttpHeaders();
        source.forEach((name, values) -> copy.put(name, new java.util.ArrayList<>(values)));
        return copy;
    }

    private JsonNode startVerification(HttpHeaders headers, String actionCode) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/verifications",
                new HttpEntity<>(Map.of("actionCode", actionCode), headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).startsWith("/verifications/");
        assertThat(response.getBody()).isNotNull();
        JsonNode result = Objects.requireNonNull(response.getBody()).path("data");
        assertThat(result.has("attemptId")).isFalse();
        return result;
    }

    private String startSatisfiedVerification(HttpHeaders headers, String actionCode) {
        JsonNode result = startVerification(headers, actionCode);
        assertThat(result.path("status").asText()).isEqualTo("SATISFIED");
        return result.path("verificationId").asText();
    }

    private String startRequiredAccountDeletionVerification(HttpHeaders headers) {
        JsonNode result = startVerification(headers, "account.delete");
        assertThat(result.path("status").asText()).isEqualTo("FACTOR_REQUIRED");
        return result.path("verificationId").asText();
    }

    private JsonNode postJson(String path, Map<String, ?> body, HttpHeaders headers) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                path,
                new HttpEntity<>(body, headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    private JsonNode postEnrollment(HttpHeaders headers) {
        ResponseEntity<JsonNode> response = http.exchange(
                "/me/authentication/totp/enrollments",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath())
                .startsWith("/me/authentication/totp/enrollments/");
        assertThat(response.getBody()).isNotNull();
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    private JsonNode createTotpCredential(Map<String, ?> body, HttpHeaders headers) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/me/authentication/totp/credential",
                new HttpEntity<>(body, headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/me/authentication/totp/credential");
        assertThat(response.getBody()).isNotNull();
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    private String enrollTotp(HttpHeaders headers) throws Exception {
        String verificationId = startSatisfiedVerification(
                headers,
                "authentication.totp.enroll");
        HttpHeaders enrollmentHeaders = copyHeaders(headers);
        enrollmentHeaders.set(AuthenticationHttpContract.VERIFICATION_HEADER, verificationId);
        JsonNode enrollment = postEnrollment(enrollmentHeaders);
        String enrollmentId = enrollment.path("enrollmentId").asText();
        ResponseEntity<byte[]> qr = http.exchange(
                "/me/authentication/totp/enrollments/" + enrollmentId + "/qr-code",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);
        assertThat(qr.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secret = querySecret(decodeQr(Objects.requireNonNull(qr.getBody())));
        createTotpCredential(
                Map.of("enrollmentId", enrollmentId, "code", totpCode(secret, Instant.now().minusSeconds(30))),
                headers);
        return secret;
    }

    private long count(String sql, long id) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, id));
    }

    private JsonNode replaceAuthenticationSettings(Map<String, ?> body, HttpHeaders headers) {
        ResponseEntity<JsonNode> current = http.exchange(
                "/me/authentication/settings",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(current.getHeaders().getETag()).isNotBlank();
        HttpHeaders replacementHeaders = copyHeaders(headers);
        replacementHeaders.setIfMatch(current.getHeaders().getETag());
        ResponseEntity<JsonNode> response = http.exchange(
                "/me/authentication/settings",
                HttpMethod.PUT,
                new HttpEntity<>(body, replacementHeaders),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    private static List<Map<String, ?>> defaultAuthenticationMethods() {
        return List.of(
                Map.of(
                        "methodCode",
                        "password",
                        "loginStartEnabled",
                        true,
                        "loginAdditionalEnabled",
                        true,
                        "sensitiveActionEnabled",
                        true),
                Map.of(
                        "methodCode",
                        "totp",
                        "loginStartEnabled",
                        false,
                        "loginAdditionalEnabled",
                        true,
                        "sensitiveActionEnabled",
                        true),
                Map.of(
                        "methodCode",
                        "passkey",
                        "loginStartEnabled",
                        true,
                        "loginAdditionalEnabled",
                        true,
                        "sensitiveActionEnabled",
                        true));
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static HttpHeaders authenticatedHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static String decodeQr(byte[] png) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    private static String querySecret(String uri) {
        return Arrays.stream(URI.create(uri).getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .filter(part -> part.length == 2 && part[0].equals("secret"))
                .map(part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }

    private static String totpCode(String secret, Instant instant) {
        return String.format(
                "%06d",
                new GoogleAuthenticator().getTotpPassword(secret, instant.toEpochMilli()));
    }
}
