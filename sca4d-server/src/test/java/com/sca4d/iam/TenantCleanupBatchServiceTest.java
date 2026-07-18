package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.iam.cleanup.TenantCleanupTask;
import com.sca4d.iam.cleanup.TenantCleanupTaskService;
import com.sca4d.iam.cleanup.service.DefaultTenantCleanupBatchService;
import com.sca4d.iam.repository.IamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;

/** 验证租户清理任务的批量领取、执行和失败记录。 */
class TenantCleanupBatchServiceTest {

    @Test
    void shouldContinueBatchAfterReleasingFailedTask() {
        IamRepository repository = mock(IamRepository.class);
        TenantCleanupTaskService taskService = mock(TenantCleanupTaskService.class);
        TenantCleanupTask failed = new TenantCleanupTask();
        failed.setTaskId(10L);
        failed.setTenantId(20L);
        TenantCleanupTask succeeded = new TenantCleanupTask();
        succeeded.setTaskId(11L);
        succeeded.setTenantId(21L);
        when(repository.claimTenantCleanupTasks(50)).thenReturn(List.of(failed, succeeded));
        Mockito.doThrow(new IllegalStateException("cleanup failed"))
                .when(taskService)
                .cleanup(failed);

        new DefaultTenantCleanupBatchService(repository, taskService).cleanupNextBatch(50);

        InOrder operations = inOrder(taskService, repository);
        operations.verify(taskService).cleanup(failed);
        operations.verify(repository).releaseTenantCleanup(10L, IllegalStateException.class.getName());
        operations.verify(taskService).cleanup(succeeded);
    }

    @Test
    void shouldNotPersistCleanupExceptionMessage() {
        IamRepository repository = mock(IamRepository.class);
        TenantCleanupTaskService taskService = mock(TenantCleanupTaskService.class);
        TenantCleanupTask task = new TenantCleanupTask();
        task.setTaskId(10L);
        task.setTenantId(20L);
        when(repository.claimTenantCleanupTasks(50)).thenReturn(List.of(task));
        Mockito.doThrow(new IllegalStateException("x".repeat(1200)))
                .when(taskService)
                .cleanup(task);

        new DefaultTenantCleanupBatchService(repository, taskService).cleanupNextBatch(50);

        verify(repository).releaseTenantCleanup(10L, IllegalStateException.class.getName());
    }

    @Test
    void shouldRejectNonPositiveBatchSizeBeforeClaiming() {
        DefaultTenantCleanupBatchService service = new DefaultTenantCleanupBatchService(
                mock(IamRepository.class), mock(TenantCleanupTaskService.class));

        assertThatThrownBy(() -> service.cleanupNextBatch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tenant 清理批次大小必须大于 0");
    }
}
