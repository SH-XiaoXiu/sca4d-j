package com.sca4d.iot.query;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.connection.DeviceConnectionQueryService;
import com.sca4d.iot.api.query.DeviceRuntimeQueryService;
import com.sca4d.iot.api.query.DeviceRuntimeSnapshot;
import com.sca4d.iot.api.query.ReportedStateSnapshot;
import com.sca4d.iot.api.state.ReportedStateStore;
import com.sca4d.iot.device.DeviceQuery;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DownlinkRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 从主库和 Redis 读取 Device 当前运行投影。 */
public class DefaultDeviceRuntimeQueryService implements DeviceRuntimeQueryService {

    private final @NotNull DeviceRepository devices;

    private final @NotNull DeviceConnectionQueryService connections;

    private final @NotNull ReportedStateStore reported;

    private final @NotNull DownlinkRepository downlinks;

    /** 创建不把 Redis 连接信息与主库状态互相覆盖的查询服务。 */
    public DefaultDeviceRuntimeQueryService(
            @NotNull DeviceRepository devices,
            @NotNull DeviceConnectionQueryService connections,
            @NotNull ReportedStateStore reported,
            @NotNull DownlinkRepository downlinks) {
        this.devices = Objects.requireNonNull(devices, "Device Repository 不能为空");
        this.connections = Objects.requireNonNull(connections, "Connection Repository 不能为空");
        this.reported = Objects.requireNonNull(reported, "ReportedStateStore 不能为空");
        this.downlinks = Objects.requireNonNull(downlinks, "下行 Repository 不能为空");
    }

    @Override
    public @NotNull DeviceRuntimeSnapshot runtime(long deviceId) {
        Device device = loadDeviceById(deviceId);
        return new DeviceRuntimeSnapshot(
                connections.connections(deviceId),
                reported.find(deviceId, device.bindingGeneration()),
                downlinks.findDesired(deviceId, device.bindingGeneration()));
    }

    /** 先取得当前绑定代数，再批量读取对应 reported，缺失 Device 不会被替换成旧绑定。 */
    @Override
    public @NotNull Map<Long, ReportedStateSnapshot> reported(
            @NotNull Collection<Long> deviceIds,
            @NotNull String groupCode) {
        Map<Long, Long> generations = new LinkedHashMap<>();
        for (Device device : devices.find(DeviceQuery.forIds(deviceIds).build()).getItems()) {
            generations.put(device.id(), device.bindingGeneration());
        }
        return reported.find(generations, Objects.requireNonNull(groupCode, "reported 字段组不能为空"));
    }

    /** 要求查询目标 Device 存在。 */
    private @NotNull Device loadDeviceById(long deviceId) {
        return devices.find(deviceId)
                .orElseThrow(() -> new BusinessException(IotErrorCode.DEVICE_NOT_FOUND));
    }
}
