package com.sca4d.core.api.connection;

import com.sca4d.core.api.authentication.SessionId;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 保存 App 对外持续网络连接的公共生命周期事实，不保存协议句柄或消息内容。 */
public class Connection {

    private final @NotNull ConnectionId id;

    private final @NotNull ConnectionProtocol protocol;

    private final @NotNull Instant connectedAt;

    private final @NotNull Instant lastActivityAt;

    private final @Nullable SessionId sessionId;

    private final @NotNull String nodeId;

    /** 创建一条已经建立的连接信息。 */
    public Connection(
            @NotNull ConnectionId id,
            @NotNull ConnectionProtocol protocol,
            @Nullable SessionId sessionId,
            @NotNull Instant connectedAt,
            @NotNull Instant lastActivityAt,
            @NotNull String nodeId) {
        this.id = Objects.requireNonNull(id, "Connection 标识不能为空");
        this.protocol = Objects.requireNonNull(protocol, "Connection 协议不能为空");
        this.sessionId = sessionId;
        this.connectedAt = Objects.requireNonNull(connectedAt, "Connection 建立时间不能为空");
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt, "Connection 最近活动时间不能为空");
        this.nodeId = Objects.requireNonNull(nodeId, "Connection 节点不能为空");
        if (lastActivityAt.isBefore(connectedAt)) {
            throw new IllegalArgumentException("Connection 最近活动时间不能早于建立时间");
        }
    }

    /**
     * 当前连接的代次标识。
     *
     * @return 不透明连接标识；不同连接代次不能复用同一个值
     */
    @Contract(pure = true)
    public @NotNull ConnectionId id() {
        return id;
    }

    /** @return 建立该连接时选择的协议 */
    @Contract(pure = true)
    public @NotNull ConnectionProtocol protocol() {
        return protocol;
    }

    /**
     * 返回建立连接时绑定的 Session。
     *
     * @return 关联的 Session；匿名连接或协议自身完成认证的连接为空
     */
    @Contract(pure = true)
    public @NotNull Optional<SessionId> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    /** @return 连接信息首次登记的时间 */
    @Contract(pure = true)
    public @NotNull Instant connectedAt() {
        return connectedAt;
    }

    /** @return 连接信息最后一次收到活动或续租的时间 */
    @Contract(pure = true)
    public @NotNull Instant lastActivityAt() {
        return lastActivityAt;
    }

    /** @return 当前持有协议句柄、可以执行断开的节点标识 */
    @Contract(pure = true)
    public @NotNull String nodeId() {
        return nodeId;
    }
}
