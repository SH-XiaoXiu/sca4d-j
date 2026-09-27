package com.sca4d.scheduling.quartz;

import com.sca4d.core.api.scheduling.JobSchedule;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 保存对账成功的代码计划，供 Quartz fire 按 key 和定义版本恢复。 */
public class QuartzScheduleRegistry {

    private final AtomicReference<Map<ScheduleKey, JobSchedule<?, ?>>> schedules = new AtomicReference<>(Map.of());

    /** 创建尚未包含代码计划的进程内目录。 */
    public QuartzScheduleRegistry() {
    }

    /**
     * 使用完整计划集合替换当前可执行目录。
     *
     * @param definitions 已完成 Quartz 数据库对账的完整计划集合
     */
    public void replace(@NotNull Map<ScheduleKey, JobSchedule<?, ?>> definitions) {
        schedules.set(Map.copyOf(Objects.requireNonNull(definitions, "Quartz 计划目录不能为空")));
    }

    /**
     * 按持久化 key 和定义版本恢复计划。
     *
     * @param key Quartz JobDetail 保存的计划 key
     * @param definitionVersion Quartz JobDetail 保存的计划定义版本
     * @return 与 key 和定义版本完全匹配的当前代码计划
     * @throws IllegalStateException 当前代码缺少计划或定义版本不一致时抛出
     */
    public @NotNull JobSchedule<?, ?> loadByKeyAndVersion(@NotNull ScheduleKey key, int definitionVersion) {
        JobSchedule<?, ?> schedule = schedules.get().get(Objects.requireNonNull(key, "ScheduleKey 不能为空"));
        if (schedule == null) {
            throw new IllegalStateException("Quartz 计划定义不存在: " + key);
        }
        if (schedule.definitionVersion() != definitionVersion) {
            throw new IllegalStateException(
                    "Quartz 计划 definitionVersion 不一致: " + key + ", expected=" + definitionVersion
                            + ", actual=" + schedule.definitionVersion());
        }
        return schedule;
    }
}
