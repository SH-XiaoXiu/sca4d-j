package com.sca4d.iot.api.connection;

import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.iot.api.DeviceAccessMethod;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 保存设备接入实现观测到的一条连接事实，不把它等同于 `Session` 或在线状态。 */
public final class DeviceConnection {

    private final @NotNull DeviceConnectionId id;

    private final long deviceId;

    private final @NotNull DeviceConnectionProtocol protocol;

    private final @Nullable SessionId sessionId;

    private final @NotNull DeviceAccessMethod accessMethod;

    private final @NotNull Instant connectedAt;

    /** 创建一条具有稳定连接代次的设备连接事实。 */
    public DeviceConnection(
            @NotNull DeviceConnectionId id,
            long deviceId,
            @NotNull DeviceConnectionProtocol protocol,
            @Nullable SessionId sessionId,
            @NotNull DeviceAccessMethod accessMethod,
            @NotNull Instant connectedAt) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Connection Device 标识必须为正数");
        }
        this.id = Objects.requireNonNull(id, "DeviceConnectionId 不能为空");
        this.deviceId = deviceId;
        this.protocol = Objects.requireNonNull(protocol, "DeviceConnectionProtocol 不能为空");
        this.sessionId = sessionId;
        this.accessMethod = Objects.requireNonNull(accessMethod, "Connection 接入方式不能为空");
        if (accessMethod.usesSession() != (sessionId != null)) {
            throw new IllegalArgumentException("Connection 接入方式与 SessionId 不一致");
        }
        this.connectedAt = Objects.requireNonNull(connectedAt, "Connection 建立时间不能为空");
    }

    /** @return 连接代次使用的不透明标识 */
    @Contract(pure = true)
    public @NotNull DeviceConnectionId id() {
        return id;
    }

    /** @return Device 标识 */
    @Contract(pure = true)
    public long deviceId() {
        return deviceId;
    }

    /** @return 建立这条连接时使用的应用协议 */
    @Contract(pure = true)
    public @NotNull DeviceConnectionProtocol protocol() {
        return protocol;
    }

    /** @return 可选 Session 标识 */
    @Contract(pure = true)
    public @NotNull Optional<SessionId> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    /** @return 本次连接采用的识别或认证方式 */
    @Contract(pure = true)
    public @NotNull DeviceAccessMethod accessMethod() {
        return accessMethod;
    }

    /** @return 接入实现报告的连接时间 */
    @Contract(pure = true)
    public @NotNull Instant connectedAt() {
        return connectedAt;
    }
}
