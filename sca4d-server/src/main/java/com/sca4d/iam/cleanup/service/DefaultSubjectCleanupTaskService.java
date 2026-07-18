package com.sca4d.iam.cleanup.service;

import com.sca4d.iam.cleanup.SubjectCleanupTask;
import com.sca4d.iam.cleanup.SubjectCleanupTaskService;
import com.sca4d.iam.profile.repository.DefaultProfileRepository;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.im.repository.ImRepository;
import com.sca4d.tenant.repository.TenantAccessRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 单个 Subject 清理任务的默认事务实现。 */
@Service
@RequiredArgsConstructor
public class DefaultSubjectCleanupTaskService implements SubjectCleanupTaskService {

    private final IamRepository iamRepository;
    private final DefaultProfileRepository defaultProfileRepository;
    private final ImRepository imRepository;
    private final TenantAccessRepository tenantAccessRepository;

    /**
     * 在同一事务中清理模板内 IAM 关系和 DEFAULT Profile，并仅在全部删除成功后完成任务。
     *
     * @param task 当前节点已经领取的 Subject 清理任务
     */
    @Transactional
    @Override
    public void cleanup(@NotNull SubjectCleanupTask task) {
        // Subject 终态删除只清理 IAM、IM 运行时关系和 DEFAULT Profile。通知、消息和文件由所属模块决定保留、匿名化、转移或删除。
        // 先按仍保留的 Principal 主键删除 IM 运行时成员，避免并行 Tenant 清理先后顺序造成无法回源的悬挂关系。
        List<Long> principalIds = iamRepository.findIsolatedSubjectPrincipalIds(task.getSubjectId());
        imRepository.deleteMembersByPrincipalIds(principalIds);
        tenantAccessRepository.deleteByPrincipalIds(principalIds);
        // 删除操作都按已隔离的 subjectId 执行，重复调用不会重建关系或改变其他 Subject。
        iamRepository.deleteSubjectRelations(task.getSubjectId());
        defaultProfileRepository.purge(task.getSubjectId());
        iamRepository.completeSubjectCleanup(task.getTaskId());
    }
}
