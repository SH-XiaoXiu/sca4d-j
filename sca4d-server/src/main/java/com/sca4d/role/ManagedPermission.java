package com.sca4d.role;

import com.sca4d.common.permission.PermissionType;
import lombok.Data;
import lombok.experimental.Accessors;

/** 角色管理中可分配的代码权限读取对象。 */
@Data
@Accessors(chain = true)
public class ManagedPermission {

    private Long permissionId;
    private Long parentId;
    private String code;
    private String nameKey;
    private String name;
    private PermissionType permissionType;
    private Integer sortOrder;
}
