package com.sca4d.web.realtime.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.authentication.SessionInvalidationListener;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import com.sca4d.web.realtime.stomp.WebRealtimeProtocol;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 使用 Redis 解析目标连接，并通过当前节点传输发送 Web Realtime 消息。 */
@ApiStatus.Internal
public final class TargetPublisher implements WebRealtimePublisher, SessionInvalidationListener {

    private static final Logger LOG = LoggerFactory.getLogger(TargetPublisher.class);

    private final @NotNull RedisConnectionRegistry registry;

    private final @NotNull NodeTransport nodeTransport;

    private final @NotNull WebRealtimeProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull ObjectMapper objectMapper;

    /** 创建浏览器实时消息发布器。 */
    public TargetPublisher(
            @NotNull RedisConnectionRegistry registry,
            @NotNull NodeTransport nodeTransport,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock,
            @NotNull ObjectMapper objectMapper) {
        this.registry = Objects.requireNonNull(registry, "Web Realtime 共享索引不能为空");
        this.nodeTransport = Objects.requireNonNull(nodeTransport, "Web Realtime 节点传输不能为空");
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        this.clock = Objects.requireNonNull(clock, "Web Realtime 时钟不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    /** 活跃事务提交后发布；没有事务时立即发布。 */
    @Override
    public void publish(@NotNull WebRealtimeTarget target, @NotNull Event<?> event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    route(target, event, false);
                }
            });
            return;
        }
        route(target, event, false);
    }

    /** 立即解析目标并发送节点消息。 */
    @Override
    public void publishNow(@NotNull WebRealtimeTarget target, @NotNull Event<?> event) {
        route(target, event, false);
    }

    /** 在事务提交后通知相关浏览器重新读取 Session，并在写出后关闭连接。 */
    @Override
    public void invalidated(long accountId) {
        Event<ObjectNode> event = new Event<>(
                new EventType<>(
                        WebRealtimeProtocol.AUTHENTICATION_SESSION_CHANGED_EVENT,
                        1,
                        ObjectNode.class),
                null,
                clock.instant(),
                JsonNodeFactory.instance.objectNode());
        Runnable action = () -> {
            try {
                route(WebRealtimeTarget.account(accountId), event, true);
            } catch (RuntimeException exception) {
                LOG.warn(
                        "Session 变化的 Web Realtime 通知失败，客户端后续请求仍会读取最新状态 | accountId={}",
                        accountId,
                        exception);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** 按节点分组连接并按配置批量发送相同浏览器信封。 */
    private void route(
            @NotNull WebRealtimeTarget target,
            @NotNull Event<?> event,
            boolean closeAfter) {
        // Redis 只返回仍在租约内且能定位持有节点的连接。
        Map<String, List<ConnectionId>> byNode = registry.resolve(target);
        Event<?> checkedEvent = Objects.requireNonNull(event, "Web Realtime 事件不能为空");
        JsonNode payload = objectMapper.valueToTree(checkedEvent.payload());
        ConnectionDelivery delivery = new ConnectionDelivery(
                new BrowserEnvelope(
                        checkedEvent.type().code(),
                        checkedEvent.type().protocolVersion(),
                        checkedEvent.occurredAt(),
                        payload),
                closeAfter);

        // 每个节点按配置上限拆分连接列表，本节点传输决定直接投递或发送 RabbitMQ 临时消息。
        int batchSize = properties.getRoutingBatchSize();
        byNode.forEach((nodeId, connections) -> {
            for (int start = 0; start < connections.size(); start += batchSize) {
                int end = Math.min(connections.size(), start + batchSize);
                nodeTransport.send(nodeId, List.copyOf(connections.subList(start, end)), delivery);
            }
        });
        LOG.trace(
                "Web Realtime 节点投递已提交 | target={} eventCode={} connectionCount={} nodeCount={}",
                target.key(),
                checkedEvent.type().code(),
                byNode.values().stream().mapToInt(List::size).sum(),
                byNode.size());
    }
}
