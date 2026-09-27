package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iam.rbac.Role;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 组合 Platform 角色授权事实和完整多语言字段。 */
@SuppressWarnings("ClassCanBeRecord")
public class PlatformRoleDetails {

    private final @NotNull Role role;

    private final @NotNull LocalizedFields i18n;

    /**
     * 创建 Platform 角色管理详情。
     *
     * @param role 不包含展示文案的角色授权事实
     * @param i18n 当前角色保存的完整多语言字段
     */
    public PlatformRoleDetails(
            @NotNull Role role,
            @NotNull LocalizedFields i18n) {
        this.role = Objects.requireNonNull(role, "角色不能为空");
        this.i18n = Objects.requireNonNull(i18n, "角色多语言字段不能为空");
    }

    /**
     * 返回不包含展示文案的角色授权事实。
     *
     * @return 角色授权事实
     */
    @Contract(pure = true)
    public @NotNull Role role() {
        return role;
    }

    /**
     * 返回角色保存的完整多语言字段。
     *
     * @return 完整多语言字段
     */
    @Contract(pure = true)
    public @NotNull LocalizedFields i18n() {
        return i18n;
    }

    /**
     * 返回名称、状态、权限和删除共享的版本。
     *
     * @return 当前版本
     */
    @Contract(pure = true)
    public @NotNull Version version() {
        return role.version();
    }
}
