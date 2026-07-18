package com.sca4d.iam.cleanup.service;

import com.sca4d.iam.cleanup.TenantCleanupTask;
import com.sca4d.iam.cleanup.TenantCleanupTaskService;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.tenant.repository.TenantAccessRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 单个 Tenant 清理任务的默认事务实现。 */
@Service
@RequiredArgsConstructor
public class DefaultTenantCleanupTaskService implements TenantCleanupTaskService {

    private final IamRepository iamRepository;
    private final DictionaryRepository dictionaryRepository;
    private final ImRepository imRepository;
    private final TenantAccessRepository tenantAccessRepository;

    /**
     * 在同一事务中清理 Tenant 的运行时关系，并仅在全部删除成功后完成任务。
     *
     * <p>租户主记录、永久编码、操作审计、通知投递和 IM 消息都是历史数据，不在该任务中删除。文件当前只表达上传
     * Principal 而不表达租户所有权，也保持原状态，避免把上传者关系误判为租户资产归属。
     *
     * @param task 当前节点已经领取的 Tenant 清理任务
     */
    @Transactional
    @Override
    public void cleanup(@NotNull TenantCleanupTask task) {
        // 先读取仍保留的已隔离 Principal 主键；同一事务内清理 IM 活跃成员后，再删除 IAM 关系和主体数据。
        List<Long> principalIds = iamRepository.findIsolatedTenantPrincipalIds(task.getTenantId());
        imRepository.deleteMembersByPrincipalIds(principalIds);
        dictionaryRepository.deleteIsolatedTenantDictionaries(task.getTenantId());
        tenantAccessRepository.deleteByTenant(task.getTenantId());
        iamRepository.deleteTenantRelations(task.getTenantId());
        iamRepository.completeTenantCleanup(task.getTaskId());
    }
}
