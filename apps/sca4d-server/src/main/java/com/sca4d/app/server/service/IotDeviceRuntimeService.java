package com.sca4d.app.server.service;

import com.sca4d.app.server.iot.IotDeviceRuntimeDetails;
import com.sca4d.iot.api.connection.DeviceOnlinePolicy;
import com.sca4d.iot.api.query.DeviceRuntimeQueryService;
import com.sca4d.iot.api.query.DeviceRuntimeSnapshot;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 读取 IoT 运行态并显式应用具体应用选择的在线策略。 */
@Service
public final class IotDeviceRuntimeService {

    private final @NotNull DeviceRuntimeQueryService runtime;

    private final @NotNull DeviceOnlinePolicy onlinePolicy;

    /** 创建应用运行态查询入口。 */
    public IotDeviceRuntimeService(
            @NotNull DeviceRuntimeQueryService runtime,
            @NotNull DeviceOnlinePolicy onlinePolicy) {
        this.runtime = Objects.requireNonNull(runtime, "Device 运行查询服务不能为空");
        this.onlinePolicy = Objects.requireNonNull(onlinePolicy, "Device 在线策略不能为空");
    }

    /**
     * 读取一次 Device 运行态，并使用当前应用的在线策略计算在线结果。
     *
     * @param deviceId Device 标识
     * @return 运行态快照和在线判断
     */
    @CheckReturnValue
    public @NotNull IotDeviceRuntimeDetails read(long deviceId) {
        DeviceRuntimeSnapshot snapshot = runtime.runtime(deviceId);
        return new IotDeviceRuntimeDetails(snapshot, onlinePolicy.isOnline(snapshot.connections()));
    }
}
