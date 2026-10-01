package com.sca4d.iot.emqx.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.iot.emqx.config.EmqxProperties;
import com.sca4d.iot.emqx.connection.EmqxClientConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 使用本地 HTTP 服务验证 EMQX Management API 的路径、认证和二进制发布契约。 */
class HttpEmqxManagementClientTest {

    private static final String API_KEY = "test-api-key";

    private static final String API_SECRET = "test-api-secret";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AtomicReference<String> authorization = new AtomicReference<>();

    private final AtomicReference<String> protocol = new AtomicReference<>();

    private final AtomicReference<JsonNode> publication = new AtomicReference<>();

    private final AtomicInteger publicationRequests = new AtomicInteger();

    private final AtomicBoolean abortPublication = new AtomicBoolean();

    private final AtomicBoolean blockPublication = new AtomicBoolean();

    private final CountDownLatch releasePublication = new CountDownLatch(1);

    private HttpServer server;

    private HttpEmqxManagementClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();

        client = createClient(Duration.ofSeconds(10));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("按 Client ID 查询在线状态并把原始 payload 以 Base64 发布")
    void findsClientAndPublishesBinaryPayload() {
        Optional<EmqxClientConnection> online = client.findClient("dbg-7");
        Optional<EmqxClientConnection> offline = client.findClient("offline");
        client.publish(
                new MqttPublication(
                        "debug/devices/dbg-7/downlink",
                        new byte[] {0x00, 0x01, (byte) 0xFF},
                        2,
                        true));

        assertThat(online).get()
                .extracting(
                        EmqxClientConnection::clientId,
                        EmqxClientConnection::brokerNode,
                        EmqxClientConnection::connectedAt)
                .containsExactly("dbg-7", "emqx@node", Instant.parse("2026-08-07T10:00:00Z"));
        assertThat(online.orElseThrow().clientAttributes()).containsEntry("device_id", "7");
        assertThat(offline).isEmpty();
        assertThat(protocol).hasValue("HTTP/1.1");
        assertThat(authorization).hasValue(
                "Basic " + Base64.getEncoder()
                        .encodeToString((API_KEY + ":" + API_SECRET).getBytes(StandardCharsets.UTF_8)));
        assertThat(publication.get()).isNotNull();
        assertThat(publication.get().path("topic").textValue()).isEqualTo("debug/devices/dbg-7/downlink");
        assertThat(publication.get().path("payload").textValue()).isEqualTo("AAH/");
        assertThat(publication.get().path("payload_encoding").textValue()).isEqualTo("base64");
        assertThat(publication.get().path("qos").intValue()).isEqualTo(2);
        assertThat(publication.get().path("retain").booleanValue()).isTrue();
    }

    @Test
    @DisplayName("发布响应中断时只发送一次并报告结果未知")
    void doesNotRetryPublishWhenResponseEndsAbruptly() {
        abortPublication.set(true);

        assertThatThrownBy(() -> client.publish(new MqttPublication("debug/custom", new byte[0], 1, false)))
                .isInstanceOf(EmqxPublishResultUnknownException.class)
                .hasRootCauseInstanceOf(IOException.class);

        assertThat(publicationRequests).hasValue(1);
    }

    @Test
    @DisplayName("把 operationTimeout 用作发布响应读取超时")
    void appliesOperationTimeoutToResponseRead() {
        client = createClient(Duration.ofMillis(200));
        blockPublication.set(true);

        try {
            assertThatThrownBy(
                    () -> client.publish(new MqttPublication("debug/custom", new byte[0], 1, false)))
                    .isInstanceOf(EmqxPublishResultUnknownException.class)
                    .hasRootCauseInstanceOf(HttpTimeoutException.class);
        } finally {
            releasePublication.countDown();
        }

        assertThat(publicationRequests).hasValue(1);
    }

    private void respond(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(exchange.getRequestMethod()) && "/api/v5/clients/dbg-7".equals(path)) {
            send(exchange, 200, """
                    {"clientid":"dbg-7","node":"emqx@node","connected_at":"2026-08-07T10:00:00Z",
                     "client_attrs":{"device_id":"7"}}
                    """);
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/v5/clients/offline".equals(path)) {
            send(exchange, 404, "{}");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/v5/publish".equals(path)) {
            protocol.set(exchange.getProtocol());
            publicationRequests.incrementAndGet();
            publication.set(JSON.readTree(exchange.getRequestBody()));
            if (blockPublication.get()) {
                try {
                    if (!releasePublication.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("测试未释放发布响应");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("等待测试释放发布响应时被中断", exception);
                }
            }
            if (abortPublication.get()) {
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        send(exchange, 500, "{}");
    }

    /** 创建使用指定连接和读取超时的测试客户端。 */
    private HttpEmqxManagementClient createClient(Duration operationTimeout) {
        EmqxProperties properties = new EmqxProperties();
        properties.setEnabled(true);
        properties.setClientId("server-test");
        properties.setOperationTimeout(operationTimeout);
        properties.setManagementBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setManagementApiKey(API_KEY);
        properties.setManagementSecret(API_SECRET);
        return new HttpEmqxManagementClient(properties);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
