package com.sca4d.iot.api.connection;

import com.sca4d.iot.api.Device;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 按直接连接协议、标识类型和值查找已登记 Device。 */
public interface DeviceConnectionResolver {

    /**
     * 查询声明了指定协议标识的 Device。
     *
     * @param connection 完整直接连接绑定
     * @return 匹配 Device；不存在时为空
     */
    @CheckReturnValue
    @NotNull
    Optional<Device> findDevice(@NotNull DeviceConnectionBinding connection);

    /**
     * 批量查询声明了指定协议标识的 Device。
     *
     * @param connections 完整直接连接绑定集合
     * @return 以匹配连接绑定为 key 的 Device；未登记绑定不出现在结果中
     */
    @CheckReturnValue
    @NotNull
    Map<DeviceConnectionBinding, Device> findDevices(
            @NotNull Collection<DeviceConnectionBinding> connections);
}
