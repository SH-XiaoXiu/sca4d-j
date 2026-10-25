package com.sca4d.iot.device;

import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 保存停用 Device 后一次直接连接绑定及认证接入方式的完整替换。 */
public class DeviceConnectionChange {

    private final @Nullable DeviceConnectionBinding connection;

    private final @NotNull Set<DeviceAccessMethod> accessMethods;

    /**
     * @param connection 新直接连接绑定；改为间接 Device 时为 {@code null}
     * @param accessMethods 新认证接入方式；没有直接连接时必须为空
     */
    public DeviceConnectionChange(
            @Nullable DeviceConnectionBinding connection,
            @NotNull Set<DeviceAccessMethod> accessMethods) {
        this.connection = connection;
        this.accessMethods = Set.copyOf(Objects.requireNonNull(accessMethods, "Device 接入方式不能为空"));
    }

    /** @return 新直接连接绑定；间接 Device 为空 */
    public @NotNull Optional<DeviceConnectionBinding> connection() {
        return Optional.ofNullable(connection);
    }

    /** @return 新认证接入方式；间接 Device 为空集合 */
    public @NotNull @Unmodifiable Set<DeviceAccessMethod> accessMethods() {
        return accessMethods;
    }
}
