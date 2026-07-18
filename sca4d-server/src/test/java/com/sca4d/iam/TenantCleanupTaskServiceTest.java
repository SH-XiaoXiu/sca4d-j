package com.sca4d.iam;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.iam.cleanup.TenantCleanupTask;
import com.sca4d.iam.cleanup.service.DefaultTenantCleanupTaskService;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.tenant.repository.TenantAccessRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

/** 验证单个 Tenant 清理任务删除租户关系且保留上传者文件关系。 */
class TenantCleanupTaskServiceTest {

    @Test
    void shouldCompleteOnlyAfterAllRuntimeRelationsAreDeleted() {
        IamRepository iamRepository = mock(IamRepository.class);
        DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
        ImRepository imRepository = mock(ImRepository.class);
        TenantAccessRepository tenantAccessRepository = mock(TenantAccessRepository.class);
        TenantCleanupTask task = new TenantCleanupTask();
        task.setTaskId(10L);
        task.setTenantId(20L);
        when(iamRepository.findIsolatedTenantPrincipalIds(20L)).thenReturn(List.of(31L, 32L));

        new DefaultTenantCleanupTaskService(
                iamRepository, dictionaryRepository, imRepository, tenantAccessRepository)
                .cleanup(task);

        InOrder operations = inOrder(iamRepository, dictionaryRepository, imRepository, tenantAccessRepository);
        operations.verify(iamRepository).findIsolatedTenantPrincipalIds(20L);
        operations.verify(imRepository).deleteMembersByPrincipalIds(List.of(31L, 32L));
        operations.verify(dictionaryRepository).deleteIsolatedTenantDictionaries(20L);
        operations.verify(tenantAccessRepository).deleteByTenant(20L);
        operations.verify(iamRepository).deleteTenantRelations(20L);
        operations.verify(iamRepository).completeTenantCleanup(10L);
    }
}
