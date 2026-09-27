package com.sca4d.iot.api.connection;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 根据具体应用已经选择的当前连接信息判断 Device 是否在线。 */
public interface DeviceOnlinePolicy {

    /**
     * @param connections 当前仍有效的连接信息
     * @return 当前证据是否满足在线条件
     */
    @CheckReturnValue
    boolean isOnline(@NotNull List<DeviceConnection> connections);
}
