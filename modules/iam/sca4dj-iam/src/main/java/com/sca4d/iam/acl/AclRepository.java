package com.sca4d.iam.acl;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.iam.DomainScope;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;

/**
 * 领域接入统一 ACL 所需实现的持久化规范。
 */
public interface AclRepository {
    /**
     * 返回当前实现负责的唯一领域范围。
     *
     * @return 领域范围
     */
    @CheckReturnValue
    @NotNull
    DomainScope scope();

    /**
     * 返回当前领域独占认领的资源类型。
     *
     * @return 资源类型
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Set<@NotNull TreeCode> resourceTypes();

    /**
     * 幂等授予一项直接权限。
     *
     * @param principalId  业务主体标识
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param action       动作
     */
    void grant(
            @NotNull Long principalId,
            @NotNull TreeCode resourceType,
            @NotNull String resourceId,
            @NotNull Action action);

    /**
     * 幂等撤销一项直接权限。
     *
     * @param principalId  业务主体标识
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param action       动作
     */
    void revoke(
            @NotNull Long principalId,
            @NotNull TreeCode resourceType,
            @NotNull String resourceId,
            @NotNull Action action);

    /**
     * 判断一项直接权限是否存在。
     *
     * @param principalId  业务主体标识
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param action       动作
     * @return 已授予时为 {@code true}
     */
    @CheckReturnValue
    boolean allows(
            @NotNull Long principalId,
            @NotNull TreeCode resourceType,
            @NotNull String resourceId,
            @NotNull Action action);

    /**
     * 查找业务主体可执行指定动作的资源实例。
     *
     * @param principalId  业务主体标识
     * @param resourceType 资源类型
     * @param action       动作
     * @return 不可修改的资源标识列表
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull String> findAllowedResourceIds(
            @NotNull Long principalId,
            @NotNull TreeCode resourceType,
            @NotNull Action action);

    /**
     * 查找业务主体在一种资源类型下的全部授权。
     *
     * @param principalId  业务主体标识
     * @param resourceType 资源类型
     * @return ACL 条目列表
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull AclEntry> findEntries(
            @NotNull Long principalId,
            @NotNull TreeCode resourceType);

    /**
     * 清理业务主体的全部 ACL。
     *
     * @param principalId 要清理全部 ACL 的业务主体标识
     */
    void revokeByPrincipal(@NotNull Long principalId);

    /**
     * 清理资源实例的全部 ACL。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     */
    void revokeByResource(@NotNull TreeCode resourceType, @NotNull String resourceId);
}
