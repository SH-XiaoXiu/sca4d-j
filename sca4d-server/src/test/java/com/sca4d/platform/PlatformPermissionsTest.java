package com.sca4d.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.tenant.TenantPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证平台权限编码、父子关系和适用 Principal 类型。 */
class PlatformPermissionsTest {

    @Test
    void shouldContributePlatformAndTenantManagementActionsWithoutDuplicateModuleRoots() {
        PermissionCatalog catalog = new PermissionCatalog(List.of(new PlatformPermissions(), new TenantPermissions()));

        assertThat(catalog.items())
                .extracting(item -> item.getCode())
                .contains(
                        PlatformPermissions.ACCOUNTS_READ,
                        PlatformPermissions.ACCOUNTS_CREATE,
                        PlatformPermissions.ACCOUNTS_UPDATE,
                        PlatformPermissions.OPERATORS_READ,
                        PlatformPermissions.OPERATORS_MANAGE,
                        PlatformPermissions.ROLES_READ,
                        PlatformPermissions.ROLES_MANAGE,
                        PlatformPermissions.TENANTS_READ,
                        PlatformPermissions.TENANTS_CREATE,
                        PlatformPermissions.TENANTS_UPDATE,
                        PlatformPermissions.TENANT_MEMBERS_READ,
                        PlatformPermissions.TENANT_MEMBERS_CREATE,
                        PlatformPermissions.TENANT_MEMBERS_UPDATE,
                        PlatformPermissions.TENANT_ROLES_READ,
                        PlatformPermissions.TENANT_ROLES_MANAGE,
                        TenantPermissions.TENANT_MEMBERS_READ,
                        TenantPermissions.TENANT_MEMBERS_CREATE,
                        TenantPermissions.TENANT_MEMBERS_UPDATE,
                        TenantPermissions.TENANT_ROLES_READ,
                        TenantPermissions.TENANT_ROLES_MANAGE);
    }
}
