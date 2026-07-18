package com.sca4d.iam.cleanup;

import org.jetbrains.annotations.NotNull;

/** 在一个事务中删除单个已领取 Tenant 的相关数据。 */
public interface TenantCleanupTaskService {

    /**
     * 幂等清理指定 Tenant 的运行时关系，并在同一事务中完成清理任务。
     *
     * @param task 当前节点已经领取、等待执行的 Tenant 清理任务
     */
    void cleanup(@NotNull TenantCleanupTask task);
}
