package com.sca4d.core.api.scheduling;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * 把当前应用的完整代码计划登记到定时实现，并删除同一 App 隔离名称内已经移除的计划。
 *
 * <p>Scheduler 只计算触发时间并提交 Job，不执行 Job 业务代码，也不保存 Job 的节点状态和结果。
 */
public interface JobScheduler {

    /**
     * 对账当前应用的全部计划。
     *
     * @param schedules 当前代码登记的完整计划集合
     * @throws IllegalStateException 计划 key 重复、定义版本倒退、同版本定义变化或定时实现写入失败时抛出
     * @throws IllegalArgumentException 计划集合、时间定义或实现所需配置无效时抛出
     */
    void reconcile(@NotNull Collection<@NotNull JobSchedule<?, ?>> schedules);
}
