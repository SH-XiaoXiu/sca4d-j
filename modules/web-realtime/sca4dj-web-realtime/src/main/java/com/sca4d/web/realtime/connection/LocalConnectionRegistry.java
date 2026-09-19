package com.sca4d.web.realtime.connection;

import com.sca4d.core.api.connection.ConnectionId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 保存当前节点的实际连接句柄，并定时续租对应 Redis 共享索引。 */
@ApiStatus.Internal
public final class LocalConnectionRegistry implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LocalConnectionRegistry.class);

    private final @NotNull RedisConnectionRegistry shared;

    private final Map<ConnectionId, LocalConnection> connections = new ConcurrentHashMap<>();

    /** 创建本机连接句柄注册表。 */
    public LocalConnectionRegistry(@NotNull RedisConnectionRegistry shared) {
        this.shared = Objects.requireNonNull(shared, "Web Realtime 共享索引不能为空");
    }

    /** 登记一个已完成 Redis 注册的本机连接。 */
    public void add(@NotNull LocalConnection connection) {
        LocalConnection checked = Objects.requireNonNull(connection, "本机连接不能为空");
        if (connections.putIfAbsent(checked.id(), checked) != null) {
            throw new IllegalStateException("本机 ConnectionId 重复: " + checked.id());
        }
    }

    /** 删除本机句柄并从 Redis 全部目标索引注销。 */
    public void remove(@NotNull ConnectionId connectionId) {
        connections.remove(connectionId);
        shared.unregister(connectionId);
    }

    /** 把 RabbitMQ 节点消息投递给当前仍存在的本机连接。 */
    public void deliver(@NotNull ConnectionId connectionId, @NotNull ConnectionDelivery delivery) {
        LocalConnection connection = connections.get(connectionId);
        if (connection != null) {
            connection.offer(delivery);
        }
    }

    /** 续租全部本机连接；共享状态已经消失或续租失败时关闭对应连接。 */
    @Scheduled(initialDelayString = "${sca4dj.web-realtime.lease-renew-interval:PT30S}", fixedDelayString = "${sca4dj.web-realtime.lease-renew-interval:PT30S}")
    public void renew() {
        for (LocalConnection connection : connections.values()) {
            try {
                if (!shared.renew(connection.id())) {
                    connection.close();
                }
            } catch (RuntimeException exception) {
                LOG.warn(
                        "Web Realtime 连接租约续期失败，连接已关闭 | connectionId={} errorType={}",
                        connection.id(),
                        exception.getClass().getSimpleName());
                connection.close();
            }
        }
    }

    /** 关闭当前节点的全部实际连接。 */
    @Override
    public void close() {
        connections.values().forEach(LocalConnection::close);
    }
}
