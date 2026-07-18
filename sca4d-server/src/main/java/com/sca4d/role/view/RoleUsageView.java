package com.sca4d.role.view;

import com.sca4d.role.ManagedRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/** 删除或停用角色前可独立读取的当前 Principal 使用量。 */
@Getter
@AllArgsConstructor
public class RoleUsageView {

    private final Long roleId;
    private final long principalCount;

    /** 从已完成范围校验的角色详情生成使用量视图。 */
    public static @NotNull RoleUsageView from(@NotNull ManagedRole role) {
        return new RoleUsageView(role.getRoleId(), role.getPrincipalCount());
    }
}
