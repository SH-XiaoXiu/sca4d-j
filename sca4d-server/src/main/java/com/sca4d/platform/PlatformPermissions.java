package com.sca4d.platform;

import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * 平台管理操作使用的 Account、操作员、租户和租户成员权限目录。
 */
@Component
public class PlatformPermissions implements PermissionModule {

    private static final String PLATFORM = "platform";
    private static final String ROLES = PLATFORM + ":roles";
    private static final String ACCOUNTS = PLATFORM + ":accounts";
    private static final String OPERATORS = PLATFORM + ":operators";
    private static final String TENANT_ACCESS = PLATFORM + ":tenant-access";
    private static final String TENANTS = PLATFORM + ":tenants";
    private static final String SCHEDULES = PLATFORM + ":schedules";
    private static final String TENANT_MEMBERS = TENANTS + ":members";
    private static final String TENANT_DICTIONARIES = TENANTS + ":dictionaries";
    private static final String TENANT_ROLES = TENANTS + ":roles";

    /** 读取平台 Principal 可分配角色和权限集合的权限编码。 */
    public static final String ROLES_READ = ROLES + ":read";

    /** 管理平台 Principal 可分配自定义角色的权限编码。 */
    public static final String ROLES_MANAGE = ROLES + ":manage";

    /** 读取 GLOBAL Account 及其登录标识、安全设置的权限编码。 */
    public static final String ACCOUNTS_READ = ACCOUNTS + ":read";

    /** 创建 GLOBAL Account 及其首个认证材料的权限编码。 */
    public static final String ACCOUNTS_CREATE = ACCOUNTS + ":create";

    /** 修改 GLOBAL Account 状态和账号资料的权限编码。 */
    public static final String ACCOUNTS_UPDATE = ACCOUNTS + ":update";

    /** 删除 GLOBAL Account，并在必要时清理孤立 Subject。 */
    public static final String ACCOUNTS_DELETE = ACCOUNTS + ":delete";

    /** 读取平台操作员及其平台角色、租户 ACL 的权限编码。 */
    public static final String OPERATORS_READ = OPERATORS + ":read";

    /** 创建、停用和配置平台操作员角色、租户 ACL 的权限编码。 */
    public static final String OPERATORS_MANAGE = OPERATORS + ":manage";

    /** 读取 PLATFORM Principal 租户 ACL 的权限编码。 */
    public static final String TENANT_ACCESS_READ = TENANT_ACCESS + ":read";

    /** 管理 PLATFORM Principal 租户 ACL 的权限编码。 */
    public static final String TENANT_ACCESS_MANAGE = TENANT_ACCESS + ":manage";

    /** 读取平台租户列表和详情的权限编码。 */
    public static final String TENANTS_READ = TENANTS + ":read";

    /** 创建租户及其内置管理员角色的权限编码。 */
    public static final String TENANTS_CREATE = TENANTS + ":create";

    /** 修改租户资料和状态的权限编码。 */
    public static final String TENANTS_UPDATE = TENANTS + ":update";

    /** 将租户推进到不可恢复删除终态的权限编码。 */
    public static final String TENANTS_DELETE = TENANTS + ":delete";

    /** 读取 Schedule 及其执行历史的权限编码。 */
    public static final String SCHEDULES_READ = SCHEDULES + ":read";

    /** 创建、完整替换、删除和人工执行 Schedule 的权限编码。 */
    public static final String SCHEDULES_MANAGE = SCHEDULES + ":manage";

    /** 读取指定租户成员、角色分配和角色选项的权限编码。 */
    public static final String TENANT_MEMBERS_READ = TENANT_MEMBERS + ":read";

    /** 将既有 Account 加入租户或在租户中创建 Account 的权限编码。 */
    public static final String TENANT_MEMBERS_CREATE = TENANT_MEMBERS + ":create";

    /** 修改租户成员状态和角色分配的权限编码。 */
    public static final String TENANT_MEMBERS_UPDATE = TENANT_MEMBERS + ":update";

    /** 读取指定租户私有字典和平台全局字典选项的权限编码。 */
    public static final String TENANT_DICTIONARIES_READ = TENANT_DICTIONARIES + ":read";

    /** 代管指定租户私有字典展示顺序和可配置内容的权限编码。 */
    public static final String TENANT_DICTIONARIES_MANAGE = TENANT_DICTIONARIES + ":manage";

    /** 读取指定租户自定义角色、权限集合和使用量的权限编码。 */
    public static final String TENANT_ROLES_READ = TENANT_ROLES + ":read";

    /** 允许平台管理员创建、修改、停用和删除指定租户自定义角色的权限编码。 */
    public static final String TENANT_ROLES_MANAGE = TENANT_ROLES + ":manage";

    /**
     * 构建平台管理操作的固定权限树。
     *
     * @return 平台模块下的 Account、操作员、租户和成员资源权限
     */
    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module(PLATFORM, "permission.platform", 10,
                PermissionNode.resource(ROLES, "permission.platform.roles", 5,
                        PermissionNode.action(ROLES_READ, "permission.platform.roles.read", 10),
                        PermissionNode.action(ROLES_MANAGE, "permission.platform.roles.manage", 20)),
                PermissionNode.resource(ACCOUNTS, "permission.platform.accounts", 11,
                        PermissionNode.action(ACCOUNTS_READ, "permission.platform.accounts.read", 10),
                        PermissionNode.action(ACCOUNTS_CREATE, "permission.platform.accounts.create", 20),
                        PermissionNode.action(ACCOUNTS_UPDATE, "permission.platform.accounts.update", 30),
                        PermissionNode.action(ACCOUNTS_DELETE, "permission.platform.accounts.delete", 40)),
                PermissionNode.resource(OPERATORS, "permission.platform.operators", 12,
                        PermissionNode.action(OPERATORS_READ, "permission.platform.operators.read", 10),
                        PermissionNode.action(OPERATORS_MANAGE, "permission.platform.operators.manage", 20)),
                PermissionNode.resource(TENANT_ACCESS, "permission.platform.tenant-access", 15,
                        PermissionNode.action(TENANT_ACCESS_READ, "permission.platform.tenant-access.read", 10),
                        PermissionNode.action(TENANT_ACCESS_MANAGE, "permission.platform.tenant-access.manage", 20)),
                PermissionNode.resource(SCHEDULES, "permission.platform.schedules", 18,
                        PermissionNode.action(SCHEDULES_READ, "permission.platform.schedules.read", 10),
                        PermissionNode.action(SCHEDULES_MANAGE, "permission.platform.schedules.manage", 20)),
                PermissionNode.resource(TENANTS, "permission.platform.tenants", 20,
                        PermissionNode.action(TENANTS_READ, "permission.platform.tenants.read", 10),
                        PermissionNode.action(TENANTS_CREATE, "permission.platform.tenants.create", 20),
                        PermissionNode.action(TENANTS_UPDATE, "permission.platform.tenants.update", 30),
                        PermissionNode.action(TENANTS_DELETE, "permission.platform.tenants.delete", 40),
                        PermissionNode.resource(TENANT_MEMBERS, "permission.platform.tenants.members", 40,
                                PermissionNode.action(TENANT_MEMBERS_READ, "permission.platform.tenants.members.read", 10),
                                PermissionNode.action(TENANT_MEMBERS_CREATE, "permission.platform.tenants.members.create", 20),
                                PermissionNode.action(TENANT_MEMBERS_UPDATE, "permission.platform.tenants.members.update", 30)),
                        PermissionNode.resource(TENANT_DICTIONARIES, "permission.platform.tenants.dictionaries", 50,
                                PermissionNode.action(TENANT_DICTIONARIES_READ, "permission.platform.tenants.dictionaries.read", 10),
                                PermissionNode.action(TENANT_DICTIONARIES_MANAGE, "permission.platform.tenants.dictionaries.manage", 20)),
                        PermissionNode.resource(TENANT_ROLES, "permission.platform.tenants.roles", 60,
                                PermissionNode.action(TENANT_ROLES_READ, "permission.platform.tenants.roles.read", 10),
                                PermissionNode.action(TENANT_ROLES_MANAGE, "permission.platform.tenants.roles.manage", 20))));
    }
}
