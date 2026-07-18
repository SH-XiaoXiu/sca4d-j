package com.sca4d.tenant;

import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * 租户管理端成员操作使用的权限目录。
 */
@Component
public class TenantPermissions implements PermissionModule {

    /** 租户管理权限树的根编码。 */
    public static final String MODULE = "tenant";

    /** 新租户默认创建的内置管理员角色编码。 */
    public static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";
    private static final String TENANT = MODULE;
    private static final String TENANT_MEMBERS = TENANT + ":members";
    private static final String TENANT_DICTIONARIES = TENANT + ":dictionaries";
    private static final String TENANT_ROLES = TENANT + ":roles";
    private static final String TENANT_IDENTITY_PROVIDERS = TENANT + ":identity-providers";

    /** 读取租户成员及其角色的权限编码。 */
    public static final String TENANT_MEMBERS_READ = TENANT_MEMBERS + ":read";

    /** 创建或加入租户成员的权限编码。 */
    public static final String TENANT_MEMBERS_CREATE = TENANT_MEMBERS + ":create";

    /** 修改租户成员状态和角色的权限编码。 */
    public static final String TENANT_MEMBERS_UPDATE = TENANT_MEMBERS + ":update";

    /** 读取当前租户可见字典和共用选项的权限编码。 */
    public static final String TENANT_DICTIONARIES_READ = TENANT_DICTIONARIES + ":read";

    /** 管理当前租户私有字典展示顺序和可配置内容的权限编码。 */
    public static final String TENANT_DICTIONARIES_MANAGE = TENANT_DICTIONARIES + ":manage";

    /** 读取当前租户角色、权限集合和使用量的权限编码。 */
    public static final String TENANT_ROLES_READ = TENANT_ROLES + ":read";

    /** 允许创建、修改、停用和删除当前租户自定义角色的权限编码。 */
    public static final String TENANT_ROLES_MANAGE = TENANT_ROLES + ":manage";

    /** 读取当前租户 IdentityProvider 通用字段和协议安全配置的权限编码。 */
    public static final String TENANT_IDENTITY_PROVIDERS_READ = TENANT_IDENTITY_PROVIDERS + ":read";

    /** 创建、修改、测试、启停和删除当前租户 IdentityProvider 的权限编码。 */
    public static final String TENANT_IDENTITY_PROVIDERS_MANAGE = TENANT_IDENTITY_PROVIDERS + ":manage";

    /**
     * 构建租户管理端成员操作的固定权限树。
     *
     * @return 租户管理端成员资源和操作权限
     */
    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module(TENANT, "permission.tenant", 20,
                PermissionNode.resource(TENANT_MEMBERS, "permission.tenant.members", 10,
                        PermissionNode.action(TENANT_MEMBERS_READ, "permission.tenant.members.read", 10),
                        PermissionNode.action(TENANT_MEMBERS_CREATE, "permission.tenant.members.create", 20),
                        PermissionNode.action(TENANT_MEMBERS_UPDATE, "permission.tenant.members.update", 30)),
                PermissionNode.resource(TENANT_DICTIONARIES, "permission.tenant.dictionaries", 20,
                        PermissionNode.action(TENANT_DICTIONARIES_READ, "permission.tenant.dictionaries.read", 10),
                        PermissionNode.action(TENANT_DICTIONARIES_MANAGE, "permission.tenant.dictionaries.manage", 20)),
                PermissionNode.resource(TENANT_ROLES, "permission.tenant.roles", 30,
                        PermissionNode.action(TENANT_ROLES_READ, "permission.tenant.roles.read", 10),
                        PermissionNode.action(TENANT_ROLES_MANAGE, "permission.tenant.roles.manage", 20)),
                PermissionNode.resource(TENANT_IDENTITY_PROVIDERS, "permission.tenant.identity-providers", 40,
                        PermissionNode.action(TENANT_IDENTITY_PROVIDERS_READ, "permission.tenant.identity-providers.read", 10),
                        PermissionNode.action(TENANT_IDENTITY_PROVIDERS_MANAGE, "permission.tenant.identity-providers.manage", 20)));
    }
}
