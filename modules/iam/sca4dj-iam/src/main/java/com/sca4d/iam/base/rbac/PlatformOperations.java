package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacOperationModule;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** 声明 Platform 角色、Account 和 Principal 角色管理使用的 Operation 模块。 */
public final class PlatformOperations implements RbacOperationModule {

    private static final @NotNull String MODULE = "platform";

    private static final @NotNull String ROLES_RESOURCE = "roles";

    private static final @NotNull String ACCOUNTS_RESOURCE = "accounts";

    private static final @NotNull String PRINCIPAL_ROLES_RESOURCE = "principal-roles";

    /** 读取 Platform 角色。 */
    public static final @NotNull String ROLES_READ = MODULE + ':' + ROLES_RESOURCE + ':' + Action.READ_VALUE;

    /** 管理 Platform 角色及其 Operation。 */
    public static final @NotNull String ROLES_MANAGE = MODULE + ':' + ROLES_RESOURCE + ':' + Action.MANAGE_VALUE;

    /** 读取 Platform Account 目录。 */
    public static final @NotNull String ACCOUNTS_READ = MODULE + ':' + ACCOUNTS_RESOURCE + ':' + Action.READ_VALUE;

    /** 创建和修改 Platform Account。 */
    public static final @NotNull String ACCOUNTS_MANAGE = MODULE + ':' + ACCOUNTS_RESOURCE + ':' + Action.MANAGE_VALUE;

    /** 读取 PlatformPrincipal 的角色。 */
    public static final @NotNull String PRINCIPAL_ROLES_READ = MODULE + ':' + PRINCIPAL_ROLES_RESOURCE + ':'
            + Action.READ_VALUE;

    /** 替换 PlatformPrincipal 的角色。 */
    public static final @NotNull String PRINCIPAL_ROLES_MANAGE = MODULE + ':' + PRINCIPAL_ROLES_RESOURCE + ':'
            + Action.MANAGE_VALUE;

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull OperationNode.Module operationTree() {
        return OperationNode.module(
                MODULE,
                "operation.platform",
                100,
                OperationNode.resource(
                        ROLES_RESOURCE,
                        "operation.platform.roles",
                        100,
                        OperationNode.action(Action.READ_VALUE, "operation.platform.roles.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.platform.roles.manage", 200)),
                OperationNode.resource(
                        ACCOUNTS_RESOURCE,
                        "operation.platform.accounts",
                        200,
                        OperationNode.action(Action.READ_VALUE, "operation.platform.accounts.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.platform.accounts.manage", 200)),
                OperationNode.resource(
                        PRINCIPAL_ROLES_RESOURCE,
                        "operation.platform.principal-roles",
                        300,
                        OperationNode.action(
                                Action.READ_VALUE,
                                "operation.platform.principal-roles.read",
                                100),
                        OperationNode.action(
                                Action.MANAGE.value(),
                                "operation.platform.principal-roles.manage",
                                200)));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Set<@NotNull RbacDomain> domains() {
        return Set.of(PlatformRbacRepository.PLATFORM_DOMAIN);
    }
}
