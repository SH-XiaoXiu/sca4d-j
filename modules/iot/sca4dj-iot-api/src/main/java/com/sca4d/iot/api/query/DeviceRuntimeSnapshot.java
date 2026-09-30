package com.sca4d.iot.api.query;

import com.sca4d.iot.api.connection.DeviceConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 保存同一 Device 当前连接、reported 和 desired 的一次查询结果。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class DeviceRuntimeSnapshot {

    private final @NotNull List<DeviceConnection> connections;

    private final @NotNull List<DesiredStateSnapshot> desired;

    private final @NotNull List<ReportedStateSnapshot> reported;

    /**
     * 创建不可修改的 Device 运行态快照。
     *
     * @param connections 查询时仍有效的连接信息
     * @param reported 当前绑定代数 下的 reported 字段组
     * @param desired 当前绑定代数 下的 desired 属性
     */
    public DeviceRuntimeSnapshot(
            @NotNull List<DeviceConnection> connections,
            @NotNull List<ReportedStateSnapshot> reported,
            @NotNull List<DesiredStateSnapshot> desired) {
        this.connections = List.copyOf(Objects.requireNonNull(connections, "Device 连接集合不能为空"));
        this.reported = List.copyOf(Objects.requireNonNull(reported, "Device reported 集合不能为空"));
        this.desired = List.copyOf(Objects.requireNonNull(desired, "Device desired 集合不能为空"));
    }

    /** @return 查询时仍有效的不可修改连接集合 */
    public @NotNull @Unmodifiable List<DeviceConnection> connections() {
        return connections;
    }

    /** @return 当前绑定代数 下的不可修改 reported 字段组 */
    public @NotNull @Unmodifiable List<ReportedStateSnapshot> reported() {
        return reported;
    }

    /** @return 当前绑定代数 下的不可修改 desired 属性 */
    public @NotNull @Unmodifiable List<DesiredStateSnapshot> desired() {
        return desired;
    }
}
