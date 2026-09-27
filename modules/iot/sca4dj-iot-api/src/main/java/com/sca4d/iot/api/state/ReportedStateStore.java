package com.sca4d.iot.api.state;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.iot.api.query.ReportedStateSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 原子维护 Device 每个字段组的 latest reported 投影，并允许具体应用替换存储产品。 */
public interface ReportedStateStore {

    /**
     * 比较顺序、合并字段并保存当前投影。
     *
     * @return 接受、重复或较旧以及当前投影版本
     * @throws BusinessException 相同顺序对应不同内容或顺序范围变化时抛出
     */
    @NotNull
    ReportedStateWriteResult update(
            long deviceId,
            long bindingGeneration,
            @NotNull ReportedStateUpdate update,
            @NotNull Map<String, Object> normalizedValues,
            @NotNull String orderScope,
            @NotNull String orderValue,
            @NotNull Instant receivedAt);

    /** @return Device 当前绑定下按字段组编码排列的 latest reported 快照 */
    @NotNull
    @Unmodifiable
    List<@NotNull ReportedStateSnapshot> find(long deviceId, long bindingGeneration);

    /** @return 各 Device 指定绑定代数下的同一字段组；缺失投影不进入结果，批量读取不逐设备往返 */
    @NotNull
    Map<Long, ReportedStateSnapshot> find(@NotNull Map<Long, Long> bindingGenerations, @NotNull String groupCode);

    /** 删除 Device 指定旧绑定下的全部 latest reported 投影。 */
    void delete(long deviceId, long bindingGeneration);
}
