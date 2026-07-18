package com.sca4d.iam.subject.service;

import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.subject.SubjectService;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建、读取、更新、停用和删除 Subject。
 *
 * <p>Subject 保存自然人的固定认证身份信息。Credential 校验、Principal 选择和 Session 管理由对应服务处理。
 */
@Service
@RequiredArgsConstructor
public class DefaultSubjectService implements SubjectService {

    private final IamRepository iamRepository;

    /**
     * 创建一个可参与认证的 Subject。
     *
     * @param displayName 展示名称；资料尚未完整时允许缺少
     * @return 新建 Subject
     */
    @Override
    public @NotNull IamSubjectEntity create(@Nullable String displayName) {
        return iamRepository.createSubject(displayName);
    }

    /**
     * 读取一个存在的 Subject。
     *
     * @param subjectId 登录身份主键
     * @return 未删除的 Subject
     * @throws BizException Subject 不存在时抛出
     */
    @Override
    public @NotNull IamSubjectEntity require(@NotNull Long subjectId) {
        // Subject 缺失表示登录身份已被删除或从未创建，后续 Credential 与 Principal 操作必须停止。
        IamSubjectEntity subject = iamRepository.findSubject(subjectId);
        if (subject == null) {
            throw new BizException(IamErrorCode.SUBJECT_NOT_FOUND);
        }
        return subject;
    }

    /**
     * 修改 Subject 的可编辑资料。
     *
     * @param subjectId 登录身份主键
     * @param displayName 新展示名称；允许清空
     * @param version 调用方读取到的乐观锁版本
     */
    @Override
    @DistributedLock(key = "'subject:' + #subjectId")
    public void update(
            @NotNull Long subjectId,
            @Nullable String displayName,
            @NotNull Long version) {
        // 乐观锁冲突由 Repository 共用转换，Service 不依赖受影响行数或具体 SQL 条件。
        iamRepository.updateSubject(subjectId, displayName, version);
    }

    /**
     * 修改 Subject 是否可参与认证的状态。
     *
     * @param subjectId 登录身份主键
     * @param status 目标启用、停用或删除状态
     * @param version 调用方读取到的乐观锁版本
     */
    @Override
    @DistributedLock(key = "'subject:' + #subjectId")
    public void changeStatus(
            @NotNull Long subjectId, @NotNull SubjectStatus status, @NotNull Long version) {
        // 这里只更新 Subject 状态；调用方负责在停用或删除后结束该 Subject 的全部会话。
        iamRepository.updateSubjectStatus(subjectId, status, version);
    }

    @Override
    @DistributedLock(key = "'subject:' + #subjectId")
    public void advanceVersion(@NotNull Long subjectId, @NotNull Long version) {
        iamRepository.advanceSubjectVersion(subjectId, version);
    }

    /**
     * 同步隔离待删除 Subject 并登记异步关系清理任务。
     *
     * @param subjectId 登录身份主键
     * @param version 调用方读取到的乐观锁版本
     */
    @Transactional
    @Override
    @DistributedLock(key = "'subject:' + #subjectId")
    public void delete(@NotNull Long subjectId, @NotNull Long version) {
        iamRepository.isolateDeletedSubject(subjectId, version);
    }
}
