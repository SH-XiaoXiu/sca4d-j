package com.sca4d.app.server.platform.view;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.iam.rbac.RolePermissions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 返回 Platform 角色当前得到的具体 ACTION。 */
@Schema(name = "PlatformRolePermissions")
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class RolePermissionsView {

    private final @NotNull List<@NotNull String> permissionCodes;

    private final @NotNull Version revision;

    private final long roleId;

    private RolePermissionsView(
            long roleId,
            @NotNull List<@NotNull String> permissionCodes,
            @NotNull Version revision) {
        this.roleId = roleId;
        this.permissionCodes = List.copyOf(permissionCodes);
        this.revision = revision;
    }

    /**
     * 从角色权限创建 HTTP 视图。
     *
     * @param permissions 当前角色与具体 Operation
     * @return HTTP 角色 ACTION 视图
     */
    @CheckReturnValue
    public static @NotNull RolePermissionsView from(@NotNull RolePermissions permissions) {
        RolePermissions checked = Objects.requireNonNull(permissions, "角色 Operation 不能为空");
        return new RolePermissionsView(
                checked.role().roleId(),
                checked.operations().stream().map(operation -> operation.code().format(COLON)).toList(),
                checked.role().version());
    }

    /**
     * 返回角色标识。
     * @return 角色标识
     */
    public long getRoleId() {
        return roleId;
    }

    /**
     * 返回角色当前得到的 ACTION 编码集合。
     * @return ACTION 编码集合
     */
    public @NotNull @Unmodifiable List<@NotNull String> getPermissionCodes() {
        return permissionCodes;
    }

    /** @return 生成当前角色权限集合的角色版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
