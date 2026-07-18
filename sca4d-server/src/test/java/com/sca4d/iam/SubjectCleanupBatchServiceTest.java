package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.iam.cleanup.SubjectCleanupTask;
import com.sca4d.iam.cleanup.SubjectCleanupTaskService;
import com.sca4d.iam.cleanup.service.DefaultSubjectCleanupBatchService;
import com.sca4d.iam.repository.IamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;

/** 验证集群批次领取并执行 Subject 清理任务。 */
class SubjectCleanupBatchServiceTest {

    @Test
    void shouldContinueBatchAfterReleasingFailedTask() {
        IamRepository repository = mock(IamRepository.class);
        SubjectCleanupTaskService cleanupTaskService = mock(SubjectCleanupTaskService.class);
        SubjectCleanupTask failed = new SubjectCleanupTask();
        failed.setTaskId(10L);
        failed.setSubjectId(20L);
        SubjectCleanupTask succeeded = new SubjectCleanupTask();
        succeeded.setTaskId(11L);
        succeeded.setSubjectId(21L);
        when(repository.claimSubjectCleanupTasks(50)).thenReturn(List.of(failed, succeeded));
        Mockito.doThrow(new IllegalStateException("cleanup failed"))
                .when(cleanupTaskService)
                .cleanup(failed);

        new DefaultSubjectCleanupBatchService(repository, cleanupTaskService).cleanupNextBatch(50);

        InOrder operations = inOrder(cleanupTaskService, repository);
        operations.verify(cleanupTaskService).cleanup(failed);
        operations.verify(repository).releaseSubjectCleanup(10L, IllegalStateException.class.getName());
        operations.verify(cleanupTaskService).cleanup(succeeded);
    }

    @Test
    void shouldNotPersistCleanupExceptionMessage() {
        IamRepository repository = mock(IamRepository.class);
        SubjectCleanupTaskService cleanupTaskService = mock(SubjectCleanupTaskService.class);
        SubjectCleanupTask task = new SubjectCleanupTask();
        task.setTaskId(10L);
        task.setSubjectId(20L);
        when(repository.claimSubjectCleanupTasks(50)).thenReturn(List.of(task));
        Mockito.doThrow(new IllegalStateException("x".repeat(1200)))
                .when(cleanupTaskService)
                .cleanup(task);

        new DefaultSubjectCleanupBatchService(repository, cleanupTaskService).cleanupNextBatch(50);

        verify(repository).releaseSubjectCleanup(10L, IllegalStateException.class.getName());
    }

    @Test
    void shouldRejectNonPositiveBatchSizeBeforeClaiming() {
        DefaultSubjectCleanupBatchService service = new DefaultSubjectCleanupBatchService(
                mock(IamRepository.class), mock(SubjectCleanupTaskService.class));

        assertThatThrownBy(() -> service.cleanupNextBatch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subject 清理批次大小必须大于 0");
    }
}
