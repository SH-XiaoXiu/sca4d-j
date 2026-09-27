package com.sca4d.iot.api.connection;

import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 保存设备连接完成识别或认证后允许接入的 Device、接入方式和有效期。 */
public class DeviceConnectionAccess {

    private final @NotNull Device device;

    private final @NotNull DeviceAccessMethod accessMethod;

    private final @Nullable SessionId sessionId;

    private final @Nullable Instant expiresAt;

    /**
     * 创建已经由 IoT 认证实现校验完成的连接接入结果。
     *
     * @param device 当前连接允许使用的 Device
     * @param accessMethod 本次连接实际采用的接入方式
     * @param sessionId 使用 `Session` 时绑定的 Session 标识
     * @param expiresAt 接入实现必须终止该认证连接的时间；没有强制失效时间时为空
     */
    public DeviceConnectionAccess(
            @NotNull Device device,
            @NotNull DeviceAccessMethod accessMethod,
            @Nullable SessionId sessionId,
            @Nullable Instant expiresAt) {
        this.device = Objects.requireNonNull(device, "连接接入 Device 不能为空");
        this.accessMethod = Objects.requireNonNull(accessMethod, "Device 接入方式不能为空");
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
        if (!device.accessMethods().contains(accessMethod)) {
            throw new IllegalArgumentException("Device 没有声明本次连接采用的接入方式");
        }
        if (accessMethod.usesSession() && sessionId == null) {
            throw new IllegalArgumentException("Session 接入方式必须绑定 SessionId");
        }
        if (!accessMethod.usesSession() && sessionId != null) {
            throw new IllegalArgumentException("非 Session 接入方式不能绑定 SessionId");
        }
    }

    /** @return 当前连接允许使用的 Device */
    @Contract(pure = true)
    public @NotNull Device device() {
        return device;
    }

    /** @return 本次连接实际采用的识别或认证方式 */
    @Contract(pure = true)
    public @NotNull DeviceAccessMethod accessMethod() {
        return accessMethod;
    }

    /** @return 使用 `Session` 时绑定的 Session 标识 */
    @Contract(pure = true)
    public @NotNull Optional<SessionId> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    /** @return 接入实现必须终止该认证连接的时间 */
    @Contract(pure = true)
    public @NotNull Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }
}
