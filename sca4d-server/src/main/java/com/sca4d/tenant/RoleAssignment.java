package com.sca4d.tenant;

import lombok.Data;

/**
 * 租户成员的角色绑定。
 *
 * <p>{@code roleCode} 是对应 IAM 角色关系的读取投影，不由成员查询或响应层生成。
 */
@Data
public class RoleAssignment {
    private Long principalId;
    private Long roleId;
    private String roleCode;
    private String roleName;
}
