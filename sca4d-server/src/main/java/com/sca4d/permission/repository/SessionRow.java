package com.sca4d.permission.repository;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.Nullable;

/**
 * IAM 会话状态 JOIN 查询的单行数据库结果。
 *
 * <p>{@code roleCode} 来自 IAM 角色定义，{@code permissionCode} 来自代码权限目录同步结果；这里只保存 JOIN
 * 投影，不产生新的开放编码。
 */
public class SessionRow {

    private Long accountId;
    private AccountScopeType accountScopeType;
    private Long accountScopeId;
    private Boolean secondFactorRequired;
    private Long subjectId;
    private String subjectDisplayName;
    private Long principalId;
    private PrincipalType principalType;
    private Long tenantId;
    private String principalDisplayName;
    private String roleCode;
    private String permissionCode;

    /** @return 当前行所属 Account 主键，数据库映射异常时为空 */
    public @Nullable Long getAccountId() {
        return accountId;
    }

    /** @param accountId 当前行所属 Account 主键 */
    public void setAccountId(@Nullable Long accountId) {
        this.accountId = accountId;
    }

    /** @return Account 的 GLOBAL 或 TENANT 作用域，数据库映射异常时为空 */
    public @Nullable AccountScopeType getAccountScopeType() {
        return accountScopeType;
    }

    /** @param accountScopeType Account 的 GLOBAL 或 TENANT 作用域 */
    public void setAccountScopeType(@Nullable AccountScopeType accountScopeType) {
        this.accountScopeType = accountScopeType;
    }

    /** @return TENANT Account 所属租户主键；GLOBAL Account 为空 */
    public @Nullable Long getAccountScopeId() {
        return accountScopeId;
    }

    /** @param accountScopeId TENANT Account 所属租户主键；GLOBAL Account 为空 */
    public void setAccountScopeId(@Nullable Long accountScopeId) {
        this.accountScopeId = accountScopeId;
    }

    /** @return 创建 Session 前是否必须完成独立第二认证因素 */
    public boolean isSecondFactorRequired() {
        return Boolean.TRUE.equals(secondFactorRequired);
    }

    /** @param secondFactorRequired 创建 Session 前是否必须完成独立第二认证因素 */
    public void setSecondFactorRequired(@Nullable Boolean secondFactorRequired) {
        this.secondFactorRequired = secondFactorRequired;
    }

    /** @return 当前行所属 Subject 主键，数据库映射异常时为空 */
    public @Nullable Long getSubjectId() {
        return subjectId;
    }

    /** @param subjectId 当前行所属 Subject 主键 */
    public void setSubjectId(@Nullable Long subjectId) {
        this.subjectId = subjectId;
    }

    /** @return 登录会话展示的 Subject 名称，资料未填写时为空 */
    public @Nullable String getSubjectDisplayName() {
        return subjectDisplayName;
    }

    /** @param subjectDisplayName 登录会话展示的 Subject 名称 */
    public void setSubjectDisplayName(@Nullable String subjectDisplayName) {
        this.subjectDisplayName = subjectDisplayName;
    }

    /** @return 当前行所属 Principal 主键，数据库映射异常时为空 */
    public @Nullable Long getPrincipalId() {
        return principalId;
    }

    /** @param principalId 当前行所属 Principal 主键 */
    public void setPrincipalId(@Nullable Long principalId) {
        this.principalId = principalId;
    }

    /** @return 当前行 Principal 类型，数据库映射异常时为空 */
    public @Nullable PrincipalType getPrincipalType() {
        return principalType;
    }

    /** @param principalType 当前行 Principal 类型 */
    public void setPrincipalType(@Nullable PrincipalType principalType) {
        this.principalType = principalType;
    }

    /** @return TENANT Principal 的租户主键，其他类型为空 */
    public @Nullable Long getTenantId() {
        return tenantId;
    }

    /** @param tenantId TENANT Principal 的租户主键 */
    public void setTenantId(@Nullable Long tenantId) {
        this.tenantId = tenantId;
    }

    /** @return 当前 Principal 的会话展示名称，未填写时为空 */
    public @Nullable String getPrincipalDisplayName() {
        return principalDisplayName;
    }

    /** @param principalDisplayName 当前 Principal 的会话展示名称 */
    public void setPrincipalDisplayName(@Nullable String principalDisplayName) {
        this.principalDisplayName = principalDisplayName;
    }

    /** @return 当前 JOIN 行命中的角色编码，没有有效角色时为空 */
    public @Nullable String getRoleCode() {
        return roleCode;
    }

    /** @param roleCode 当前 JOIN 行命中的角色编码 */
    public void setRoleCode(@Nullable String roleCode) {
        this.roleCode = roleCode;
    }

    /** @return 当前 JOIN 行命中的明确权限编码，没有有效权限时为空 */
    public @Nullable String getPermissionCode() {
        return permissionCode;
    }

    /** @param permissionCode 当前 JOIN 行命中的明确权限编码 */
    public void setPermissionCode(@Nullable String permissionCode) {
        this.permissionCode = permissionCode;
    }
}
