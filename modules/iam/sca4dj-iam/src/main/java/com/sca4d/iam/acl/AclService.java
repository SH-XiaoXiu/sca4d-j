package com.sca4d.iam.acl;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iam.DomainScope;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.iam.identity.IdentityService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 校验并路由指定领域的 ACL 操作。 */
@SuppressWarnings("unused")
public class AclService {
    private static final Logger LOG = LoggerFactory.getLogger(AclService.class);

    private final @NotNull AclRepositoryRegistry repositories;

    private final @NotNull AclResourceTypes resourceTypes;

    private final @NotNull IdentityService identities;

    /**
     * 创建统一 ACL 服务。
     *
     * @param repositories 领域 Repository 注册表
     * @param resourceTypes 资源类型归属表
     * @param identities 身份事实服务
     */
    public AclService(
            @NotNull AclRepositoryRegistry repositories,
            @NotNull AclResourceTypes resourceTypes,
            @NotNull IdentityService identities) {
        this.repositories = Objects.requireNonNull(repositories, "ACL Repository 注册表不能为空");
        this.resourceTypes = Objects.requireNonNull(resourceTypes, "ACL 资源类型不能为空");
        this.identities = Objects.requireNonNull(identities, "身份服务不能为空");
    }

    /**
     * 幂等授予资源动作权限。
     *
     * @param scope 领域范围
     * @param principalId 业务主体标识
     * @param type 资源类型
     * @param id 资源标识
     * @param action 动作
     */
    @Transactional
    public void grant(
            @NotNull DomainScope scope,
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull String id,
            @NotNull Action action) {
        validate(scope, principalId, type, id, action);
        repositories.repository(scope).grant(principalId, type, id, action);
        LOG.trace(
                "ACL 授权写入已执行 | scope={} principalId={} resourceType={} resourceId={} action={}",
                scope.value(),
                principalId,
                type,
                id,
                action.value());
    }

    /**
     * 幂等撤销资源动作权限。
     *
     * @param scope 领域范围
     * @param principalId 业务主体标识
     * @param type 资源类型
     * @param id 资源标识
     * @param action 动作
     */
    @Transactional
    public void revoke(
            @NotNull DomainScope scope,
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull String id,
            @NotNull Action action) {
        validate(scope, principalId, type, id, action);
        repositories.repository(scope).revoke(principalId, type, id, action);
        LOG.trace(
                "ACL 撤销写入已执行 | scope={} principalId={} resourceType={} resourceId={} action={}",
                scope.value(),
                principalId,
                type,
                id,
                action.value());
    }

    /**
     * 判断业务主体是否拥有资源动作权限。
     *
     * @param scope 领域范围
     * @param principalId 业务主体标识
     * @param type 资源类型
     * @param id 资源标识
     * @param action 动作
     * @return 已授予时为 {@code true}
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public boolean allows(
            @NotNull DomainScope scope,
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull String id,
            @NotNull Action action) {
        validate(scope, principalId, type, id, action);
        boolean allowed = repositories.repository(scope).allows(principalId, type, id, action);
        LOG.trace(
                "ACL 权限已判断 | scope={} principalId={} resourceType={} resourceId={} action={} allowed={}",
                scope.value(),
                principalId,
                type,
                id,
                action.value(),
                allowed);
        return allowed;
    }

    /**
     * 查找业务主体可执行指定动作的资源实例。
     *
     * @param scope 领域范围
     * @param principalId 业务主体标识
     * @param type 资源类型
     * @param action 动作
     * @return 不可修改的资源标识列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull String> findAllowedResourceIds(
            @NotNull DomainScope scope,
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull Action action) {
        validate(scope, principalId, type, "*", action);
        List<String> resourceIds = List.copyOf(
                repositories.repository(scope).findAllowedResourceIds(principalId, type, action));
        LOG.debug(
                "ACL 资源范围已读取 | scope={} principalId={} resourceType={} action={} resourceCount={}",
                scope.value(),
                principalId,
                type,
                action.value(),
                resourceIds.size());
        return resourceIds;
    }

    /**
     * 查找业务主体在一种资源类型下的全部授权。
     *
     * @param scope 领域范围
     * @param principalId 业务主体标识
     * @param type 资源类型
     * @return 不可修改的 ACL 条目列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull AclEntry> findEntries(
            @NotNull DomainScope scope,
            @NotNull Long principalId,
            @NotNull String type) {
        validate(scope, principalId, type, "*", Action.READ);
        List<AclEntry> entries = List.copyOf(repositories.repository(scope).findEntries(principalId, type));
        LOG.debug(
                "ACL 条目已读取 | scope={} principalId={} resourceType={} entryCount={}",
                scope.value(),
                principalId,
                type,
                entries.size());
        return entries;
    }

    /**
     * 撤销业务主体在一个领域内的全部 ACL。
     *
     * <p>清理操作不要求 Principal 仍处于启用状态，以便领域在停用或删除身份后移除残留授权。
     *
     * @param scope 领域范围
     * @param principalId 业务主体标识
     */
    @Transactional
    public void revokeByPrincipal(@NotNull DomainScope scope, @NotNull Long principalId) {
        Long checkedPrincipalId = Objects.requireNonNull(principalId, "业务主体标识不能为空");
        // ACL 可以在主体停用或删除后成为孤立数据，因此清理路径只校验标识，不校验当前身份状态。
        repositories.repository(scope).revokeByPrincipal(checkedPrincipalId);
        LOG.trace(
                "Principal ACL 清理写入已执行 | scope={} principalId={}",
                scope.value(),
                checkedPrincipalId);
    }

    /**
     * 撤销资源实例在一个领域内的全部 ACL。
     *
     * @param scope 领域范围
     * @param type 资源类型
     * @param id 资源标识
     */
    @Transactional
    public void revokeByResource(
            @NotNull DomainScope scope,
            @NotNull String type,
        @NotNull String id) {
        validateResource(scope, type);
        TextUtils.requireExactText(id, "资源标识");
        repositories.repository(scope).revokeByResource(type, id);
        LOG.trace(
                "Resource ACL 清理写入已执行 | scope={} resourceType={} resourceId={}",
                scope.value(),
                type,
                id);
    }

    /** 校验通用 ACL 写入和读取都只能使用启用主体与已认领资源类型。 */
    private void validate(
            @NotNull DomainScope scope,
            @NotNull Long principalId,
            @NotNull String type,
            @NotNull String id,
            @NotNull Action action) {
        if (!identities.isActivePrincipal(Objects.requireNonNull(principalId, "业务主体标识不能为空"))) {
            throw new BusinessException(IamErrorCode.PRINCIPAL_UNAVAILABLE);
        }
        validateResource(scope, type);
        TextUtils.requireExactText(id, "资源标识");
        Objects.requireNonNull(action, "动作不能为空");
    }

    /** 验证资源类型属于调用方指定领域，阻止跨领域 Repository 路由。 */
    private void validateResource(@NotNull DomainScope scope, @NotNull String type) {
        String checkedType = TextUtils.requireExactText(type, "资源类型");
        // 类型归属在启动时已验证唯一；这里同时阻止调用方用错误 scope 访问另一领域的数据。
        if (!resourceTypes.scope(checkedType).equals(scope)) {
            throw new IllegalArgumentException("ACL 资源类型不属于指定 scope");
        }
    }
}
