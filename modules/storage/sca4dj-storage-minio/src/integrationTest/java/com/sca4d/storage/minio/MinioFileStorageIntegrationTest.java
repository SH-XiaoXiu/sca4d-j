package com.sca4d.storage.minio;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.storage.StorageObject;
import com.sca4d.core.api.storage.StorageUpload;
import com.sca4d.core.api.storage.testing.FileStorageContract;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** 使用真实 MinIO 和 Nginx 验证对象操作以及两种公开域名部署形态共用的 path-style 签名。 */
@Testcontainers
@SuppressWarnings("HttpUrlsUsage") // 隔离测试通过容器内 HTTP 验证 MinIO path-style 签名和 Nginx 代理。
class MinioFileStorageIntegrationTest extends FileStorageContract {

    private static final String ACCESS_KEY = "sca4dj-test";

    private static final String BUCKET = "sca4dj-files";

    private static final String SECRET_KEY = "sca4dj-test-secret";

    private static final Network NETWORK = Network.newNetwork();

    @Container
    @SuppressWarnings("resource") // Testcontainers 扩展负责测试类结束时停止静态容器。
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withNetwork(NETWORK)
            .withNetworkAliases("minio")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Container
    @SuppressWarnings("resource") // Testcontainers 扩展负责测试类结束时停止静态容器。
    private static final GenericContainer<?> NGINX = new GenericContainer<>(DockerImageName.parse("nginx:1.27-alpine"))
            .withNetwork(NETWORK)
            .dependsOn(MINIO)
            .withCopyToContainer(
                    Transferable.of("""
                            server {
                                listen 8080;
                                location /__BUCKET__/ {
                                    proxy_pass http://minio:9000;
                                    proxy_set_header Host $http_host;
                                    proxy_request_buffering off;
                                }
                                location / { return 404; }
                            }
                            """.replace("__BUCKET__", BUCKET)),
                    "/etc/nginx/conf.d/default.conf")
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort());

    private static MinioFileStorage storage;

    private static Clock clock;

    @BeforeAll
    static void createStorage() {
        clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        MinioStorageProperties properties = new MinioStorageProperties();
        properties.setEndpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        properties.setPublicEndpoint("http://" + NGINX.getHost() + ":" + NGINX.getMappedPort(8080));
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setBucket(BUCKET);
        properties.setCreateBucket(true);
        MinioClient internal = client(properties.getEndpoint(), properties);
        MinioClient publiclySigned = client(properties.getPublicEndpoint(), properties);
        storage = new MinioFileStorage(properties, internal, publiclySigned, clock);
        storage.checkAvailable();
    }

    @Override
    protected MinioFileStorage storage() {
        return storage;
    }

    @Test
    @DisplayName("通过单域名 Bucket 路径代理执行签名 PUT 和 GET")
    void transfersThroughTheBucketPathOnASharedOrigin() throws Exception {
        StorageObject object = new StorageObject(BUCKET, "example-app:sca4dj:storage/uploads/nginx.png");
        byte[] content = new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0
        };
        StorageUpload upload = storage.prepareUpload(
                201L,
                object,
                "image/png",
                content.length,
                clock.instant().plusSeconds(300));

        assertThat(upload.url().getPath()).isEqualTo("/" + BUCKET + "/" + object.objectKey());
        HttpResponse<byte[]> accepted = sendPut(upload.url(), upload.headers().get("Content-Type"), content);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(storage.inspect(object).orElseThrow().contentType()).isEqualTo("image/png");

        URI accessUrl = storage.prepareAccess(201L, object, clock.instant().plusSeconds(300));
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(accessUrl).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).containsExactly(content);
        }
        storage.delete(object);
    }

    private static HttpResponse<byte[]> sendPut(URI url, String contentType, byte[] content)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url)
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static MinioClient client(String endpoint, MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .region(properties.getRegion())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
