package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.storage.FileErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/** 通过示例 App HTTP 接口验证头像上传、绑定、读取、替换、删除和权限结果。 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = Sca4dApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sca4dj.iam.bootstrap.enabled=true",
        "sca4dj.iam.bootstrap.username=admin",
        "sca4dj.auth.password.bootstrap.enabled=true",
        "sca4dj.auth.password.bootstrap.username=admin",
        "sca4dj.auth.password.bootstrap.password=admin@123",
        "sca4dj.auth.session-ttl=PT1H",
        "sca4dj.storage.transfer-base-url=http://localhost",
        "spring.autoconfigure.exclude=com.sca4d.messaging.config.MessagingAutoConfiguration"
})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(ProfileAvatarWorkflowIntegrationTest.TestMessagingConfiguration.class)
class ProfileAvatarWorkflowIntegrationTest {

    private static final byte[] FIRST_AVATAR = "first-avatar-content".getBytes(StandardCharsets.UTF_8);

    private static final byte[] SECOND_AVATAR = "second-avatar-content".getBytes(StandardCharsets.UTF_8);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private final TestRestTemplate http;

    ProfileAvatarWorkflowIntegrationTest(TestRestTemplate http) {
        this.http = http;
    }

    /** 为不验证通知发送的头像测试替代已经明确排除的 Messaging 实现。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestMessagingConfiguration {

        /** @return 头像流程不会调用的消息发布接口 */
        @Bean
        MessageBus messagePublisher() {
            return mock(MessageBus.class);
        }
    }

    @Test
    @DisplayName("Uploads Replaces Reads And Deletes Own Avatar Through Public HTTP Results")
    void uploadsReplacesReadsAndDeletesOwnAvatarThroughPublicHttpResults() {
        HttpHeaders adminHeaders = authenticatedHeaders(login("admin", "admin@123"));
        long accountId = createAccount(adminHeaders, "avatar-owner", "avatar@123");
        HttpHeaders ownerHeaders = authenticatedHeaders(login("avatar-owner", "avatar@123"));

        long firstFileId = upload(ownerHeaders, accountId, FIRST_AVATAR, "image/png");
        ResponseEntity<JsonNode> firstBinding = putJson(
                "/me/profile/avatar",
                Map.of("fileId", firstFileId),
                ownerHeaders);
        assertThat(firstBinding.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                Objects.requireNonNull(firstBinding.getBody())
                        .path("data")
                        .path("avatarFileId")
                        .asLong())
                .isEqualTo(firstFileId);
        assertContent(ownerHeaders, firstFileId, MediaType.IMAGE_PNG, FIRST_AVATAR);

        long secondFileId = upload(ownerHeaders, accountId, SECOND_AVATAR, "image/webp");
        ResponseEntity<JsonNode> replacement = putJson(
                "/me/profile/avatar",
                Map.of("fileId", secondFileId),
                ownerHeaders);
        assertThat(replacement.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                Objects.requireNonNull(replacement.getBody())
                        .path("data")
                        .path("avatarFileId")
                        .asLong())
                .isEqualTo(secondFileId);
        assertContent(ownerHeaders, secondFileId, MediaType.parseMediaType("image/webp"), SECOND_AVATAR);
        assertFileUnavailable(ownerHeaders, firstFileId);

        ResponseEntity<JsonNode> deletion = http.exchange(
                "/me/profile/avatar",
                HttpMethod.DELETE,
                new HttpEntity<>(ownerHeaders),
                JsonNode.class);
        assertThat(deletion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                Objects.requireNonNull(deletion.getBody())
                        .path("data")
                        .path("avatarFileId")
                        .isNull())
                .isTrue();
        assertFileUnavailable(ownerHeaders, secondFileId);
    }

    @Test
    @DisplayName("Allows Administrators To Maintain Other Avatars And Hides Unauthorized Reads")
    void allowsAdministratorsToMaintainOtherAvatarsAndHidesUnauthorizedReads() {
        HttpHeaders adminHeaders = authenticatedHeaders(login("admin", "admin@123"));
        long targetAccountId = createAccount(adminHeaders, "avatar-target", "target@123");
        long otherAccountId = createAccount(adminHeaders, "avatar-other", "other@123");
        HttpHeaders targetHeaders = authenticatedHeaders(login("avatar-target", "target@123"));
        HttpHeaders otherHeaders = authenticatedHeaders(login("avatar-other", "other@123"));

        ResponseEntity<JsonNode> forbiddenUpload = http.postForEntity(
                "/files/uploads",
                new HttpEntity<>(uploadRequest(targetAccountId, FIRST_AVATAR, "image/png"), otherHeaders),
                JsonNode.class);
        assertThat(forbiddenUpload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        long fileId = upload(adminHeaders, targetAccountId, FIRST_AVATAR, "image/png");
        ResponseEntity<JsonNode> binding = putJson(
                "/platform/accounts/" + targetAccountId + "/profile/avatar",
                Map.of("fileId", fileId),
                adminHeaders);
        assertThat(binding.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                Objects.requireNonNull(binding.getBody())
                        .path("data")
                        .path("avatarFileId")
                        .asLong())
                .isEqualTo(fileId);

        ResponseEntity<JsonNode> profile = http.exchange(
                "/me/profile",
                HttpMethod.GET,
                new HttpEntity<>(targetHeaders),
                JsonNode.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(profile.getBody()).path("data").has("version")).isFalse();
        ResponseEntity<JsonNode> unchanged = putJson(
                "/me/profile/avatar",
                Map.of("fileId", fileId),
                targetHeaders);
        assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(unchanged.getBody()).path("data").path("avatarFileId").asLong())
                .isEqualTo(fileId);
        assertThat(unchanged.getBody().path("data").has("version")).isFalse();

        assertThat(accessUrls(targetHeaders, fileId)).hasSize(1);
        assertThat(accessUrls(adminHeaders, fileId)).hasSize(1);
        assertFileUnavailable(otherHeaders, fileId);

        ResponseEntity<JsonNode> anonymous = http.postForEntity(
                "/files/uploads",
                new HttpEntity<>(uploadRequest(otherAccountId, FIRST_AVATAR, "image/png"), jsonHeaders()),
                JsonNode.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** 创建一个拥有默认角色的 Platform Account 并返回账号标识。 */
    private long createAccount(HttpHeaders adminHeaders, String username, String password) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/platform/accounts",
                new HttpEntity<>(
                        Map.of(
                                "username",
                                username,
                                "initialPassword",
                                password,
                                "displayName",
                                username),
                        adminHeaders),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody()).path("data").path("id").asLong();
    }

    /** 申请、原样 PUT 并完成一份头像上传。 */
    private long upload(HttpHeaders headers, long targetAccountId, byte[] content, String contentType) {
        ResponseEntity<JsonNode> created = http.postForEntity(
                "/files/uploads",
                new HttpEntity<>(uploadRequest(targetAccountId, content, contentType), headers),
                JsonNode.class);
        assertThat(created.getStatusCode())
                .as("upload response=%s", created.getBody())
                .isEqualTo(HttpStatus.CREATED);
        JsonNode upload = Objects.requireNonNull(created.getBody()).path("data");
        assertThat(upload.path("method").asText()).isEqualTo("PUT");
        assertThat(upload.has("backendId")).isFalse();
        assertThat(upload.has("container")).isFalse();
        assertThat(upload.has("uploadGrant")).isFalse();
        long fileId = upload.path("fileId").asLong();

        HttpHeaders transferHeaders = new HttpHeaders();
        Iterator<Map.Entry<String, JsonNode>> fields = upload.path("headers").properties().iterator();
        fields.forEachRemaining(field -> transferHeaders.set(field.getKey(), field.getValue().asText()));
        ResponseEntity<Void> transferred = http.exchange(
                relative(upload.path("url").asText()),
                HttpMethod.PUT,
                new HttpEntity<>(content, transferHeaders),
                Void.class);
        assertThat(transferred.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> completed = http.postForEntity(
                "/files/uploads/" + fileId + "/completion",
                new HttpEntity<>(headers),
                JsonNode.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return fileId;
    }

    /** 创建头像上传申请请求体。 */
    private static Map<String, Object> uploadRequest(
            long targetAccountId,
            byte[] content,
            String contentType) {
        return Map.of(
                "targetAccountId",
                Long.toString(targetAccountId),
                "filename",
                "avatar.bin",
                "contentType",
                contentType,
                "sizeBytes",
                content.length);
    }

    /** 读取并校验当前账号可以通过短期地址取得指定头像内容。 */
    private void assertContent(
            HttpHeaders headers,
            long fileId,
            MediaType contentType,
            byte[] expectedContent) {
        JsonNode access = accessUrls(headers, fileId);
        assertThat(access).hasSize(1);
        ResponseEntity<byte[]> content = http.exchange(
                relative(access.get(0).path("url").asText()),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                byte[].class);
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getHeaders().getContentType()).isEqualTo(contentType);
        assertThat(content.getBody()).isEqualTo(expectedContent);
    }

    /** 断言文件不能再签发读取地址，并且响应不暴露内部状态或权限原因。 */
    private void assertFileUnavailable(HttpHeaders headers, long fileId) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/files/access-urls",
                new HttpEntity<>(Map.of("fileIds", new long[] {fileId}), headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Objects.requireNonNull(response.getBody()).path("code").asText())
                .isEqualTo(FileErrorCode.NOT_FOUND.code());
    }

    /** 批量申请一个文件的读取地址并返回响应数组。 */
    private JsonNode accessUrls(HttpHeaders headers, long fileId) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/files/access-urls",
                new HttpEntity<>(Map.of("fileIds", new long[] {fileId}), headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(response.getBody()).path("data");
    }

    /** 调用 JSON PUT 路由。 */
    private ResponseEntity<JsonNode> putJson(String path, Object body, HttpHeaders headers) {
        return http.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** 登录并返回 Session access Token。 */
    private String login(String identifier, String password) {
        ResponseEntity<JsonNode> response = http.postForEntity(
                "/login/attempts",
                new HttpEntity<>(Map.of("identifier", identifier, "password", password), jsonHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody())
                .path("data")
                .path("session")
                .path("accessToken")
                .asText();
    }

    /** 创建携带 Bearer Token 的 JSON 请求头。 */
    private static HttpHeaders authenticatedHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** 创建 JSON 请求头。 */
    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** 把签名 URL 转换为随机端口测试服务可以调用的相对地址。 */
    private static String relative(String url) {
        URI uri = URI.create(url);
        return uri.getRawQuery() == null
                ? uri.getRawPath()
                : uri.getRawPath() + "?" + uri.getRawQuery();
    }
}
