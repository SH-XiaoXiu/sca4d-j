package com.sca4d.role.view;

import com.sca4d.iam.principal.RoleStatus;
import com.sca4d.role.ManagedRole;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/** 角色定义、权限集合、内置属性和当前使用量的管理视图。 */
@Data
@Accessors(chain = true)
public class RoleView {

    private Long roleId;
    private Long tenantId;
    private String code;
    private String name;
    private RoleStatus status;
    private boolean builtIn;
    private long principalCount;
    private long permissionCount;
    private List<RolePermissionView> permissions;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    /** 将 ManagedRole 转换为不包含关系表字段的 HTTP 响应。 */
    public static @NotNull RoleView from(@NotNull ManagedRole role) {
        return new RoleView()
                .setRoleId(role.getRoleId())
                .setTenantId(role.getTenantId())
                .setCode(role.getCode())
                .setName(role.getName())
                .setStatus(role.getStatus())
                .setBuiltIn(Boolean.TRUE.equals(role.getBuiltIn()))
                .setPrincipalCount(role.getPrincipalCount())
                .setPermissionCount(role.getPermissionCount())
                .setPermissions(role.getPermissions().stream().map(RolePermissionView::from).toList())
                .setCreatedAt(role.getCreatedAt())
                .setUpdatedAt(role.getUpdatedAt())
                .setVersion(role.getVersion());
    }
}
