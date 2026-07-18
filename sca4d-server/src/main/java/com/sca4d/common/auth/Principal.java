package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 当前执行主体。
 *
 * <p>Principal 表示本次操作以哪个业务主体执行。审计、数据归属和权限判断应优先使用 Principal。
 */
public class Principal {

    private Long principalId;
    private PrincipalType principalType;
    private Long subjectId;
    private Long tenantId;
    private String displayName;

    public Principal() {
    }

    public @Nullable Long getPrincipalId() {
        return principalId;
    }

    /**
     * 获取已经完成选择的 Principal 主键。
     *
     * <p>空构造对象允许在反序列化和测试装配阶段暂时缺少主键；进入审计、归属或持久化操作前必须通过本
     * 方法显式确认选择结果完整。
     *
     * @return 当前 Principal 主键
     * @throws MissingAuthException Principal 尚未完成选择时抛出
     */
    public @NotNull Long requirePrincipalId() {
        if (principalId == null) {
            throw new MissingAuthException();
        }
        return principalId;
    }

    public void setPrincipalId(@NotNull Long principalId) {
        this.principalId = principalId;
    }

    public @Nullable PrincipalType getPrincipalType() {
        return principalType;
    }

    /**
     * 获取已经完成选择的 Principal 类型。
     *
     * <p>空构造对象允许在反序列化和测试装配阶段暂时缺少类型；进入授权、寻址或持久化操作前必须通过本方法显式确认选择结果完整。
     *
     * @return 当前 Principal 类型
     * @throws MissingAuthException Principal 尚未完成选择时抛出
     */
    public @NotNull PrincipalType requirePrincipalType() {
        if (principalType == null) {
            throw new MissingAuthException();
        }
        return principalType;
    }

    public void setPrincipalType(@NotNull PrincipalType principalType) {
        this.principalType = principalType;
    }

    public @Nullable Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(@Nullable Long subjectId) {
        this.subjectId = subjectId;
    }

    public @Nullable Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(@Nullable Long tenantId) {
        this.tenantId = tenantId;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    /**
     * 创建当前执行主体上下文。
     *
     * @param principalId Principal 主键
     * @param principalType Principal 类型
     * @param subjectId 登录身份主键
     * @param tenantId 租户主键
     * @return 当前执行主体上下文
     */
    public static @NotNull Principal of(
            @NotNull Long principalId,
            @NotNull PrincipalType principalType,
            @Nullable Long subjectId,
            @Nullable Long tenantId) {
        Principal principal = new Principal();
        principal.setPrincipalId(principalId);
        principal.setPrincipalType(principalType);
        principal.setSubjectId(subjectId);
        principal.setTenantId(tenantId);
        return principal;
    }

    /**
     * 按登录身份创建默认执行主体上下文。
     *
     * @param subjectId 登录身份主键
     * @param principalType Principal 类型
     * @param tenantId 租户主键
     * @param displayName 展示名称
     * @return 当前执行主体上下文
     */
    public static @NotNull Principal fromSubject(
            @NotNull Long subjectId,
            @NotNull PrincipalType principalType,
            @Nullable Long tenantId,
            @Nullable String displayName) {
        return withDisplayName(subjectId, principalType, subjectId, tenantId, displayName);
    }

    /**
     * 创建带展示名称的执行主体上下文。
     *
     * @param principalId Principal 主键
     * @param principalType Principal 类型
     * @param subjectId 登录身份主键
     * @param tenantId 租户主键
     * @param displayName 展示名称
     * @return 当前执行主体上下文
     */
    public static @NotNull Principal withDisplayName(
            @NotNull Long principalId,
            @NotNull PrincipalType principalType,
            @Nullable Long subjectId,
            @Nullable Long tenantId,
            @Nullable String displayName) {
        Principal principal = of(principalId, principalType, subjectId, tenantId);
        principal.setDisplayName(displayName);
        return principal;
    }
}
