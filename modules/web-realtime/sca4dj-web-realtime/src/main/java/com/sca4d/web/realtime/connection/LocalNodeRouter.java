package com.sca4d.web.realtime.connection;

import com.sca4d.core.api.connection.ConnectionId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 在单个 JVM 内把 Web Realtime 事件直接交给本机连接。 */
@ApiStatus.Internal
public class LocalNodeRouter implements NodeRouter {

    private static final Logger LOG = LoggerFactory.getLogger(LocalNodeRouter.class);

    private final @NotNull String nodeId = UUID.randomUUID().toString();

    private final @NotNull LocalConnectionRegistry connections;

    /** 创建不依赖外部消息代理的本机节点传输。 */
    public LocalNodeRouter(@NotNull LocalConnectionRegistry connections) {
        this.connections = Objects.requireNonNull(connections, "本机连接注册表不能为空");
        LOG.info("Web Realtime 节点传输已启用 | mode=LOCAL nodeId={}", nodeId);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String nodeId() {
        return nodeId;
    }

    /** 只投递当前 JVM 持有的连接；其他节点的连接由 Redis 按租约自动清理。 */
    @Override
    public void send(
            @NotNull String destinationNodeId,
            @NotNull List<@NotNull ConnectionId> connectionIds,
            @NotNull BrowserWrite delivery) {
        String destination = Objects.requireNonNull(destinationNodeId, "目标节点标识不能为空");
        List<ConnectionId> connections = List.copyOf(
                Objects.requireNonNull(connectionIds, "目标连接不能为空"));
        BrowserWrite checkedDelivery = Objects.requireNonNull(delivery, "连接投递不能为空");
        if (!nodeId.equals(destination)) {
            LOG.trace(
                    "本地 Web Realtime 跳过其他节点的连接 | destinationNodeId={} connectionCount={}",
                    destination,
                    connections.size());
            return;
        }
        connections.forEach(connectionId -> this.connections.deliver(connectionId, checkedDelivery));
    }
}
