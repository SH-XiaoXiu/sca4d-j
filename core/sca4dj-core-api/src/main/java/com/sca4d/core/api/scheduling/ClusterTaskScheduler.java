package com.sca4d.core.api.scheduling;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/** 把基础设施集群任务计划登记到集群定时实现。 */
public interface ClusterTaskScheduler {

    /**
     * 对账当前应用的全部基础设施集群任务计划。
     *
     * @param schedules 当前代码登记的完整计划集合
     * @throws IllegalStateException 计划 key 重复、定义版本倒退、同版本定义变化或定时实现写入失败时抛出
     * @throws IllegalArgumentException 计划集合、时间定义或实现所需配置无效时抛出
     */
    void reconcileClusterTasks(@NotNull Collection<@NotNull ClusterTaskSchedule> schedules);
}
