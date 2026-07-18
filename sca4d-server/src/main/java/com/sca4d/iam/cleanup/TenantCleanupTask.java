package com.sca4d.iam.cleanup;

import lombok.Data;

/** 集群节点已经原子领取的 Tenant 关系清理任务。 */
@Data
public class TenantCleanupTask {

    private Long taskId;
    private Long tenantId;
}
