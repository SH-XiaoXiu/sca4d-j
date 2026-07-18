package com.sca4d.iam;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.sca4d.iam.cleanup.SubjectCleanupTask;
import com.sca4d.iam.cleanup.service.DefaultSubjectCleanupTaskService;
import com.sca4d.iam.profile.repository.DefaultProfileRepository;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.tenant.repository.TenantAccessRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;

/** 验证单个 Subject 清理任务按规定顺序删除关联数据。 */
class SubjectCleanupTaskServiceTest {

    @Test
    void shouldCompleteTaskOnlyAfterRuntimeRelationsAndProfileAreDeleted() {
        IamRepository iamRepository = mock(IamRepository.class);
        DefaultProfileRepository defaultProfileRepository = mock(DefaultProfileRepository.class);
        ImRepository imRepository = mock(ImRepository.class);
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        SubjectCleanupTask task = new SubjectCleanupTask();
        task.setTaskId(10L);
        task.setSubjectId(20L);

        Mockito.when(iamRepository.findIsolatedSubjectPrincipalIds(20L))
                .thenReturn(List.of(31L, 32L));
        new DefaultSubjectCleanupTaskService(
                iamRepository, defaultProfileRepository, imRepository, tenantAccessRepository)
                .cleanup(task);

        InOrder operations = inOrder(iamRepository, defaultProfileRepository, imRepository, tenantAccessRepository);
        operations.verify(iamRepository).findIsolatedSubjectPrincipalIds(20L);
        operations.verify(imRepository).deleteMembersByPrincipalIds(List.of(31L, 32L));
        operations.verify(tenantAccessRepository).deleteByPrincipalIds(List.of(31L, 32L));
        operations.verify(iamRepository).deleteSubjectRelations(20L);
        operations.verify(defaultProfileRepository).purge(20L);
        operations.verify(iamRepository).completeSubjectCleanup(10L);
    }
}
