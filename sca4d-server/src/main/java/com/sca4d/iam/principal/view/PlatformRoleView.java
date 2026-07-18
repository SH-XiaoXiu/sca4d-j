package com.sca4d.iam.principal.view;

import com.sca4d.iam.principal.PlatformRole;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** PLATFORM Principal 角色绑定与选择项。 */
@Data
@Accessors(chain = true)
public class PlatformRoleView {
    private Long roleId;
    private String code;
    private String name;

    /** @param role 平台角色读取对象 @return 不暴露关系表字段的角色视图 */
    public static @NotNull PlatformRoleView from(@NotNull PlatformRole role) {
        return new PlatformRoleView()
                .setRoleId(role.getRoleId())
                .setCode(role.getRoleCode())
                .setName(role.getRoleName());
    }
}
