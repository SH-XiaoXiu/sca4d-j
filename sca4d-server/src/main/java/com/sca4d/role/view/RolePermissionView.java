package com.sca4d.role.view;

import com.sca4d.common.permission.PermissionType;
import com.sca4d.role.ManagedPermission;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 角色管理中带数据库主键和代码目录含义的权限节点。 */
@Data
@Accessors(chain = true)
public class RolePermissionView {

    private Long permissionId;
    private Long parentId;
    private String code;
    private String nameKey;
    private String name;
    private PermissionType permissionType;
    private Integer sortOrder;

    /** 将 ManagedPermission 转换为角色详情中的权限项。 */
    public static @NotNull RolePermissionView from(@NotNull ManagedPermission permission) {
        return new RolePermissionView()
                .setPermissionId(permission.getPermissionId())
                .setParentId(permission.getParentId())
                .setCode(permission.getCode())
                .setNameKey(permission.getNameKey())
                .setName(permission.getName())
                .setPermissionType(permission.getPermissionType())
                .setSortOrder(permission.getSortOrder());
    }
}
