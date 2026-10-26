package com.sca4d.iot.api.query;

import com.sca4d.core.api.error.exception.BusinessException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/** 读取 Device 当前连接、reported 和 desired 投影。 */
public interface DeviceRuntimeQueryService {

    /**
     * 一次读取 Device 当前运行态；Device 不存在时抛出 IoT 资源不存在错误。
     *
     * <p>连接来自可恢复的 Redis 投影，reported 来自配置的当前投影存储，desired 来自主 PostgreSQL；
     * 两类属性使用 Device 当前绑定代数，返回结果不表示三个存储点的事务快照。
     *
     * @param deviceId Device 标识
     * @return 调用期间读取到的不可修改运行态快照
     * @throws BusinessException Device 不存在时抛出
     */
    @NotNull
    DeviceRuntimeSnapshot runtime(long deviceId);

    /**
     * 批量读取各 Device 当前绑定的指定 reported 字段组，不读取连接和 desired。
     *
     * @param deviceIds Device 标识集合
     * @param groupCode reported 字段组编码
     * @return 按 Device 标识索引的快照；Device 或字段组不存在时不包含对应键
     */
    @NotNull
    Map<Long, ReportedStateSnapshot> reported(@NotNull Collection<Long> deviceIds, @NotNull String groupCode);
}
