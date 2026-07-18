package com.sca4d.iam;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.iam.cleanup.TenantCleanupBatchService;
import com.sca4d.iam.cleanup.TenantCleanupJob;
import org.junit.jupiter.api.Test;

/** 验证 Quartz Job 调用 Tenant 批量清理服务。 */
class TenantCleanupJobTest {

    @Test
    void shouldDelegateScheduledBatchToBusinessService() {
        TenantCleanupBatchService batchService = mock(TenantCleanupBatchService.class);

        new TenantCleanupJob(batchService).cleanupDeletedTenants();

        verify(batchService).cleanupNextBatch(50);
    }
}
