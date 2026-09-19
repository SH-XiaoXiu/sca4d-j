package com.sca4d.iot.api.connection;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/** 接收具体设备接入实现已经校验并转换后的连接和断线事实。 */
public interface DeviceConnectionReceiver {

    /**
     * 登记或刷新一个明确的连接代次。
     *
     * @param connection 已经解析到 Device 的连接事实
     */
    void connected(@NotNull DeviceConnection connection);

    /**
     * 删除仍与指定协议和连接代次一致的当前连接事实。
     *
     * @param protocol 产生断线事实的连接协议
     * @param connectionId 已经结束的连接代次
     * @return 当前连接事实是否被删除
     */
    boolean disconnected(@NotNull DeviceConnectionProtocol protocol, @NotNull DeviceConnectionId connectionId);

    /**
     * 用一种接入实现当前能够确认的完整连接集合修复该协议的共享投影。
     *
     * @param protocol 本次对账覆盖的连接协议
     * @param connections 当前仍存在的完整连接集合
     * @param observedAt 读取接入实现连接集合前记录的时间；较新的投影不属于本次删除范围
     */
    void reconciled(
            @NotNull DeviceConnectionProtocol protocol,
            @NotNull List<DeviceConnection> connections,
            @NotNull Instant observedAt);
}
