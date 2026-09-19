package com.sca4d.scheduling.quartz;

import com.sca4d.core.api.scheduling.ClusterTaskSchedule;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 保存完成 Quartz 对账的集群任务计划，供 Quartz fire 恢复任务定义。 */
public final class QuartzClusterTaskRegistry {

    private final AtomicReference<Map<ScheduleKey, ClusterTaskSchedule>> schedules = new AtomicReference<>(Map.of());

    /** 创建尚未包含集群任务计划的目录。 */
    public QuartzClusterTaskRegistry() {
    }

    /** 使用完整计划集合替换当前可执行目录。 */
    public void replace(@NotNull Map<ScheduleKey, ClusterTaskSchedule> definitions) {
        schedules.set(Map.copyOf(Objects.requireNonNull(definitions, "Quartz 集群任务目录不能为空")));
    }

    /** 按持久化 key 和定义版本恢复集群任务计划。 */
    public @NotNull ClusterTaskSchedule loadByKeyAndVersion(@NotNull ScheduleKey key, int definitionVersion) {
        ClusterTaskSchedule schedule = schedules.get().get(Objects.requireNonNull(key, "集群任务 ScheduleKey 不能为空"));
        if (schedule == null) {
            throw new IllegalStateException("Quartz 集群任务定义不存在: " + key);
        }
        if (schedule.definitionVersion() != definitionVersion) {
            throw new IllegalStateException(
                    "Quartz 集群任务 definitionVersion 不一致: " + key + ", expected=" + definitionVersion
                            + ", actual=" + schedule.definitionVersion());
        }
        return schedule;
    }
}
