package com.sca4d.iam;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.iam.cleanup.SubjectCleanupBatchService;
import com.sca4d.iam.cleanup.SubjectCleanupJob;
import org.junit.jupiter.api.Test;

/** 验证 Quartz Job 调用 Subject 批量清理服务。 */
class SubjectCleanupJobTest {

    @Test
    void shouldDelegateScheduledBatchToBusinessService() {
        SubjectCleanupBatchService batchService = mock(SubjectCleanupBatchService.class);

        new SubjectCleanupJob(batchService).cleanupDeletedIdentities();

        verify(batchService).cleanupNextBatch(50);
    }
}
