package com.sca4d.iot.connection;

import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.connection.DeviceOnlinePolicy;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 只要当前连接投影中存在一条连接事实，就把 Device 判定为在线。 */
public final class ConnectionPresentDeviceOnlinePolicy implements DeviceOnlinePolicy {

    /** @return 连接集合非空时为 {@code true} */
    @Override
    public boolean isOnline(@NotNull List<DeviceConnection> connections) {
        return !Objects.requireNonNull(connections, "Device 连接集合不能为空").isEmpty();
    }
}
