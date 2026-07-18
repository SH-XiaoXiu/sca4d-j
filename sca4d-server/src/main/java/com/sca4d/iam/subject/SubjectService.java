package com.sca4d.iam.subject;

import com.sca4d.entity.IamSubjectEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 创建和读取 Subject，并修改 Subject 是否允许参与认证。 */
public interface SubjectService {

    /**
     * 创建一个可参与认证的 Subject。
     *
     * @param displayName 展示名称；资料尚未完整时为 {@code null}
     * @return 已持久化且处于 ACTIVE 状态的 Subject
     */
    @NotNull
    IamSubjectEntity create(@Nullable String displayName);

    /**
     * 读取一个存在且未删除的 Subject。
     *
     * @param subjectId 全局登录身份主键
     * @return 当前 Subject
     * @throws com.sca4d.common.exception.BizException Subject 不存在时抛出
     */
    @NotNull
    IamSubjectEntity require(@NotNull Long subjectId);

    /**
     * 原子更新 Subject 的可编辑资料。
     *
     * @param subjectId 全局登录身份主键
     * @param displayName 新展示名称；为 {@code null} 时清空
     * @param version 调用方读取到的乐观锁版本
     */
    void update(
            @NotNull Long subjectId,
            @Nullable String displayName,
            @NotNull Long version);

    /**
     * 修改 Subject 是否能参与认证。
     *
     * @param subjectId 全局登录身份主键
     * @param status 目标启用、停用或删除状态
     * @param version 调用方读取到的乐观锁版本
     */
    void changeStatus(@NotNull Long subjectId, @NotNull SubjectStatus status, @NotNull Long version);

    /**
     * 在聚合内其他敏感数据变更前原子推进 Subject 版本。
     *
     * @param subjectId 全局登录身份主键
     * @param version 调用方读取到的乐观锁版本
     */
    void advanceVersion(@NotNull Long subjectId, @NotNull Long version);

    /**
     * 将 Subject 置为不可恢复的删除终态。
     *
     * @param subjectId 全局登录身份主键
     * @param version 调用方读取到的乐观锁版本
     */
    void delete(@NotNull Long subjectId, @NotNull Long version);
}
