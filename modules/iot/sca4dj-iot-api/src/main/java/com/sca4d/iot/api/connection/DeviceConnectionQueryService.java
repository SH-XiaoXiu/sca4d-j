package com.sca4d.iot.api.connection;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 批量读取 Device 当前连接信息，不解释业务在线状态。 */
public interface DeviceConnectionQueryService {

    /**
     * 批量读取当前连接；返回值包含全部去重后的请求标识，没有连接时对应空列表。
     *
     * @param deviceIds 正数 Device 标识；重复值按首次出现合并
     * @return 按请求顺序保存的不可修改 Device 到连接集合映射
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Map<@NotNull Long, @NotNull List<@NotNull DeviceConnection>> connections(
            @NotNull Collection<@NotNull Long> deviceIds);

    /**
     * 使用批量查询实现读取一个 Device 的当前连接。
     *
     * @param deviceId 正数 Device 标识
     * @return 当前不可修改连接集合
     */
    @CheckReturnValue
    default @NotNull @Unmodifiable List<@NotNull DeviceConnection> connections(long deviceId) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Device 标识必须为正数");
        }
        return Objects.requireNonNull(
                connections(List.of(deviceId)).get(deviceId),
                "批量连接查询必须返回请求的 Device 标识");
    }
}
