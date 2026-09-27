package com.sca4d.iam.acl;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.iam.DomainScope;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 对 ACL 注册的资源类型执行直接授权判断。
 */
public final class AclPermissionPolicy implements PermissionPolicy {
    /**
     * 表示对一种资源类型进行集合权限判断的保留资源标识。
     */
    public static final @NotNull String COLLECTION_ID = "*";

    private final @NotNull AclResourceTypes resourceTypes;

    private final @NotNull AclService acl;

    /**
     * 创建 ACL 权限策略。
     *
     * @param resourceTypes ACL 已认领的资源类型
     * @param acl           统一 ACL 服务
     */
    public AclPermissionPolicy(@NotNull AclResourceTypes resourceTypes, @NotNull AclService acl) {
        this.resourceTypes = Objects.requireNonNull(resourceTypes, "ACL 资源类型不能为空");
        this.acl = Objects.requireNonNull(acl, "ACL 服务不能为空");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckReturnValue
    public boolean supports(@NotNull Resource resource) {
        return resourceTypes.contains(resource.type());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckReturnValue
    public @NotNull PermissionDecision decide(
            @NotNull Call call,
            @NotNull Permission permission) {
        Optional<Principal> principal = call.principal();
        if (principal.isEmpty()) {
            return PermissionDecision.deny("没有选定业务主体");
        }
        TreeCode type = permission.resource().type();
        DomainScope scope = resourceTypes.scope(type);
        // 集合权限不能只返回允许/拒绝，还必须把可访问实例范围交给之后查询的约束。
        if (COLLECTION_ID.equals(permission.resource().id())) {
            List<String> ids = acl
                    .findAllowedResourceIds(scope, principal.get().principalId(), type, permission.action());
            return ids.isEmpty()
                    ? PermissionDecision.deny("ACL 没有允许的资源")
                    : PermissionDecision.allow(new AclResourceScope(type, ids));
        }
        return acl.allows(scope, principal.get().principalId(), type, permission.resource().id(), permission.action())
                ? PermissionDecision.allow()
                : PermissionDecision.deny("ACL 未授予该权限");
    }
}
