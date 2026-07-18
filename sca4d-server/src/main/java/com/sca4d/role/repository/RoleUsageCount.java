package com.sca4d.role.repository;

import lombok.Data;
import lombok.experimental.Accessors;

/** 一批角色当前绑定的未删除 Principal 数量投影。 */
@Data
@Accessors(chain = true)
public class RoleUsageCount {

    private Long roleId;
    private Long principalCount;
    private Long permissionCount;
}
