package com.sca4d.common.audit;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.Nullable;

/** 操作发生时采集的 Subject、Account、Principal 和租户显示快照。 */
public class AuditActor {

    private Long subjectId;
    private Long accountId;
    private String subjectDisplayName;
    private Long principalId;
    private PrincipalType principalType;
    private String principalDisplayName;
    private Long tenantId;

    /** @return 认证 Subject 标识，匿名或系统调用时为 null */
    public @Nullable Long getSubjectId() {
        return subjectId;
    }

    /** @param subjectId 操作发生时的认证 Subject 标识 */
    public void setSubjectId(@Nullable Long subjectId) {
        this.subjectId = subjectId;
    }

    /** @return 认证使用的 Account 标识，匿名或系统调用未建立 Session 时为 null */
    public @Nullable Long getAccountId() {
        return accountId;
    }

    /** @param accountId 操作发生时认证使用的 Account 标识 */
    public void setAccountId(@Nullable Long accountId) {
        this.accountId = accountId;
    }

    /** @return Subject 当时的展示名称，未认证或无法采集时为 null */
    public @Nullable String getSubjectDisplayName() {
        return subjectDisplayName;
    }

    /** @param subjectDisplayName Subject 在操作发生时的展示名称快照 */
    public void setSubjectDisplayName(@Nullable String subjectDisplayName) {
        this.subjectDisplayName = subjectDisplayName;
    }

    /** @return 实际执行权限判断的 Principal 标识，未选择 Principal 时为 null */
    public @Nullable Long getPrincipalId() {
        return principalId;
    }

    /** @param principalId 实际执行权限判断的 Principal 标识 */
    public void setPrincipalId(@Nullable Long principalId) {
        this.principalId = principalId;
    }

    /** @return 执行 Principal 的固定类型，未选择 Principal 时为 null */
    public @Nullable PrincipalType getPrincipalType() {
        return principalType;
    }

    /** @param principalType 执行 Principal 的固定类型 */
    public void setPrincipalType(@Nullable PrincipalType principalType) {
        this.principalType = principalType;
    }

    /** @return Principal 当时的展示名称，未选择 Principal 时为 null */
    public @Nullable String getPrincipalDisplayName() {
        return principalDisplayName;
    }

    /** @param principalDisplayName Principal 在操作发生时的展示名称快照 */
    public void setPrincipalDisplayName(@Nullable String principalDisplayName) {
        this.principalDisplayName = principalDisplayName;
    }

    /** @return Principal 所属租户标识，平台 Principal 或未选择 Principal 时为 null */
    public @Nullable Long getTenantId() {
        return tenantId;
    }

    /** @param tenantId Principal 在操作发生时所属的租户标识 */
    public void setTenantId(@Nullable Long tenantId) {
        this.tenantId = tenantId;
    }
}
