package com.sca4d.tenant.view;

import com.sca4d.tenant.TenantRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * 成员管理页面可选择的租户角色。
 *
 * <p>{@code code} 来自当前租户 IAM 角色定义，前端只展示和提交角色主键，不创建角色编码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantRoleView {

    private Long roleId;
    private String code;
    private String name;

    /**
     * 将租户角色读取对象转换为管理端响应。
     *
     * @param role 当前租户中的有效角色
     * @return 角色选择项
     */
    public static @NotNull TenantRoleView from(@NotNull TenantRole role) {
        return new TenantRoleView(role.getRoleId(), role.getCode(), role.getName());
    }
}
