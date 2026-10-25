package com.sca4d.iot.emqx.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.iot.emqx.config.EmqxProperties;
import com.sca4d.iot.emqx.connection.EmqxClientConnection;
import com.sca4d.iot.emqx.exception.IotEmqxException;
import com.sca4d.iot.emqx.management.exception.EmqxPublishResultUnknownException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 使用 Basic API Key 和 HTTP/1.1 调用 EMQX `5.8.9` Management API。 */
public class HttpEmqxManagementClient implements EmqxManagementClient {

    private final @NotNull RestClient client;

    private final int pageSize;

    /** 创建不记录 API secret 的 EMQX Management API 客户端。 */
    public HttpEmqxManagementClient(@NotNull EmqxProperties properties) {
        EmqxProperties checked = Objects.requireNonNull(properties, "EMQX 配置不能为空");
        checked.validate();
        Duration operationTimeout = Objects.requireNonNull(checked.getOperationTimeout());
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(operationTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(operationTimeout);
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(Objects.requireNonNull(checked.getManagementBaseUrl()))
                .defaultHeaders(
                        headers -> headers.setBasicAuth(
                                Objects.requireNonNull(checked.getManagementApiKey()),
                                Objects.requireNonNull(checked.getManagementSecret())))
                .build();
        this.pageSize = checked.getManagementPageSize();
    }

    /** DELETE 返回 404 表示目标已经离线，仍满足幂等终止结果。 */
    @Override
    public void disconnect(@NotNull String clientId) {
        try {
            client.delete()
                    .uri("/api/v5/clients/{clientid}", Objects.requireNonNull(clientId, "clientId 不能为空"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404)
                throw exception;
        }
    }

    /** 逐页读取直到 EMQX 返回空页或不足一页。 */
    @Override
    public @NotNull List<EmqxClientConnection> findClients() {
        List<EmqxClientConnection> result = new ArrayList<>();
        for (int page = 1;; page++) {
            int currentPage = page;
            JsonNode root = client.get()
                    .uri(
                            builder -> builder.path("/api/v5/clients")
                                    .queryParam("page", currentPage)
                                    .queryParam("limit", pageSize)
                                    .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray()) {
                throw new IotEmqxException("EMQX clients 响应缺少 data 数组");
            }
            for (JsonNode value : data)
                result.add(client(value));
            if (data.size() < pageSize)
                return List.copyOf(result);
        }
    }

    /** GET 返回 404 表示指定 MQTT Client 当前离线。 */
    @Override
    public @NotNull Optional<EmqxClientConnection> findClient(@NotNull String clientId) {
        try {
            JsonNode response = client.get()
                    .uri("/api/v5/clients/{clientid}", Objects.requireNonNull(clientId, "clientId 不能为空"))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.isObject()) {
                throw new IllegalStateException("EMQX client 响应不是对象");
            }
            return Optional.of(client(response));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /** 使用 Base64 编码提交原始 payload，不把消息内容写入日志。 */
    @Override
    public void publish(@NotNull MqttPublication publication) {
        MqttPublication checked = Objects.requireNonNull(publication, "MQTT 发布内容不能为空");
        try {
            client.post()
                    .uri("/api/v5/publish")
                    .body(
                            Map.of(
                                    "topic",
                                    checked.topic(),
                                    "payload",
                                    Base64.getEncoder().encodeToString(checked.payload()),
                                    "payload_encoding",
                                    "base64",
                                    "qos",
                                    checked.qos(),
                                    "retain",
                                    checked.retain()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw new EmqxPublishResultUnknownException(exception);
        }
    }

    /** 把 Management API JSON 转成连接对账值。 */
    private static EmqxClientConnection client(JsonNode node) {
        DataOps.JsonPipe json = DataOps.j(node.toString());
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        JsonNode values = node.path("client_attrs");
        if (values.isObject()) {
            values.properties().forEach(entry -> attributes.put(entry.getKey(), entry.getValue().asText()));
        }
        return new EmqxClientConnection(
                json.requiredText("clientid"),
                json.requiredText("node"),
                Instant.parse(json.requiredText("connected_at")),
                attributes);
    }
}
