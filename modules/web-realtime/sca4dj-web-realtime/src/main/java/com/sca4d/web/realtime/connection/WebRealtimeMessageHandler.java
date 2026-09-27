package com.sca4d.web.realtime.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.webrealtime.WebRealtimeMessage;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 消费 WebRealtimeMessage，并通过共享索引和 NodeRouter 写入目标浏览器连接缓冲。 */
@ApiStatus.Internal
public final class WebRealtimeMessageHandler implements MessageHandler<WebRealtimeMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(WebRealtimeMessageHandler.class);

    private static final TreeCode CODE = TreeCode.of("WEB_REALTIME/DELIVERY");

    private final @NotNull RedisConnectionRegistry registry;

    private final @NotNull NodeRouter nodeRouter;

    private final @NotNull WebRealtimeProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull ObjectMapper json;

    private final @NotNull WebRealtimeMessageCodec codec;

    public WebRealtimeMessageHandler(
            @NotNull RedisConnectionRegistry registry,
            @NotNull NodeRouter nodeRouter,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock,
            @NotNull ObjectMapper json,
            @NotNull WebRealtimeMessageCodec codec) {
        this.registry = Objects.requireNonNull(registry, "Web Realtime 共享索引不能为空");
        this.nodeRouter = Objects.requireNonNull(nodeRouter, "Web Realtime 节点路由不能为空");
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        this.clock = Objects.requireNonNull(clock, "Web Realtime 时钟不能为空");
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
        this.codec = Objects.requireNonNull(codec, "Web Realtime Message Codec 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull MessageCodec<WebRealtimeMessage> codec() {
        return codec;
    }

    @Override
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<WebRealtimeMessage> incoming) {
        WebRealtimeMessage message = Objects.requireNonNull(incoming, "Web Realtime 消息不能为空").message();
        if (!message.expiresAt().isAfter(clock.instant())) {
            LOG.trace(
                    "Web Realtime 消息已过期 | messageId={} target={} eventCode={}",
                    incoming.messageId(),
                    message.target().key(),
                    message.browserEvent().type().code());
            return HandlingDecision.acknowledge();
        }
        route(message);
        return HandlingDecision.acknowledge();
    }

    private void route(@NotNull WebRealtimeMessage message) {
        Map<String, List<ConnectionId>> byNode = registry.resolve(message.target());
        Event<?> event = message.browserEvent();
        JsonNode payload = json.valueToTree(event.payload());
        BrowserWrite write = new BrowserWrite(
                new BrowserEnvelope(
                        event.type().code(),
                        event.type().protocolVersion(),
                        event.occurredAt(),
                        payload),
                message.closeAfter());
        int batchSize = properties.getRoutingBatchSize();
        byNode.forEach((nodeId, connections) -> {
            for (int start = 0; start < connections.size(); start += batchSize) {
                int end = Math.min(connections.size(), start + batchSize);
                nodeRouter.send(nodeId, List.copyOf(connections.subList(start, end)), write);
            }
        });
        LOG.trace(
                "Web Realtime 节点投递已提交 | target={} eventCode={} connectionCount={} nodeCount={}",
                message.target().key(),
                event.type().code(),
                byNode.values().stream().mapToInt(List::size).sum(),
                byNode.size());
    }
}
