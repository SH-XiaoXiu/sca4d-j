package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacOperationModule;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * 声明 Platform 角色、Account 和 Principal 角色管理使用的 Operation 模块。
 */
public class PlatformOperations implements RbacOperationModule {

    private static final @NotNull TreeCode MODULE = TreeCode.ofSegment("PLATFORM");

    private static final @NotNull TreeCode ROLES_RESOURCE = TreeCode.ofSegment("ROLES");

    private static final @NotNull TreeCode ACCOUNTS_RESOURCE = TreeCode.ofSegment("ACCOUNTS");

    private static final @NotNull TreeCode PRINCIPAL_ROLES_RESOURCE = TreeCode.ofSegment("PRINCIPAL_ROLES");

    /**
     * 读取 Platform 角色。
     */
    public static final @NotNull String ROLES_READ_VALUE = "PLATFORM:ROLES:READ";

    public static final @NotNull TreeCode ROLES_READ = TreeCode.ofColon(ROLES_READ_VALUE);

    /**
     * 管理 Platform 角色和 Operation。
     */
    public static final @NotNull String ROLES_MANAGE_VALUE = "PLATFORM:ROLES:MANAGE";

    public static final @NotNull TreeCode ROLES_MANAGE = TreeCode.ofColon(ROLES_MANAGE_VALUE);

    /**
     * 读取 Platform Account 目录。
     */
    public static final @NotNull String ACCOUNTS_READ_VALUE = "PLATFORM:ACCOUNTS:READ";

    public static final @NotNull TreeCode ACCOUNTS_READ = TreeCode.ofColon(ACCOUNTS_READ_VALUE);

    /**
     * 创建和修改 Platform Account。
     */
    public static final @NotNull String ACCOUNTS_MANAGE_VALUE = "PLATFORM:ACCOUNTS:MANAGE";

    public static final @NotNull TreeCode ACCOUNTS_MANAGE = TreeCode.ofColon(ACCOUNTS_MANAGE_VALUE);

    /**
     * 读取 PlatformPrincipal 的角色。
     */
    public static final @NotNull String PRINCIPAL_ROLES_READ_VALUE = "PLATFORM:PRINCIPAL_ROLES:READ";

    public static final @NotNull TreeCode PRINCIPAL_ROLES_READ = TreeCode.ofColon(PRINCIPAL_ROLES_READ_VALUE);

    /**
     * 替换 PlatformPrincipal 的角色。
     */
    public static final @NotNull String PRINCIPAL_ROLES_MANAGE_VALUE = "PLATFORM:PRINCIPAL_ROLES:MANAGE";

    public static final @NotNull TreeCode PRINCIPAL_ROLES_MANAGE = TreeCode.ofColon(PRINCIPAL_ROLES_MANAGE_VALUE);

    /**
     * {@inheritDoc}
     */
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
                        OperationNode.action(Action.READ.code(), "operation.platform.roles.read", 100),
                        OperationNode.action(Action.MANAGE.code(), "operation.platform.roles.manage", 200)),
                OperationNode.resource(
                        ACCOUNTS_RESOURCE,
                        "operation.platform.accounts",
                        200,
                        OperationNode.action(Action.READ.code(), "operation.platform.accounts.read", 100),
                        OperationNode.action(Action.MANAGE.code(), "operation.platform.accounts.manage", 200)),
                OperationNode.resource(
                        PRINCIPAL_ROLES_RESOURCE,
                        "operation.platform.principal_roles",
                        300,
                        OperationNode.action(
                                Action.READ.code(),
                                "operation.platform.principal_roles.read",
                                100),
                        OperationNode.action(
                                Action.MANAGE.code(),
                                "operation.platform.principal_roles.manage",
                                200)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Set<@NotNull RbacDomain> domains() {
        return Set.of(PlatformRbacRepository.PLATFORM_DOMAIN);
    }
}
