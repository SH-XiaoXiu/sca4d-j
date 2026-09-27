package com.sca4d.app.server.platform.request;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.validation.Req;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 接收 Platform 角色 ACTION 的完整替换集合。 */
@Schema(name = "ReplacePlatformRolePermissionsRequest")
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class ReplaceRolePermissionsRequest {

    @Req.NotNull
    private @Nullable List<@NotBlank @NotNull String> permissionCodes = new ArrayList<>();

    /** 创建供 JSON 反序列化填充的空请求。 */
    public ReplaceRolePermissionsRequest() {
    }

    /**
     * 返回新 ACTION 编码集合。
     * @return 新 ACTION 编码集合
     */
    public @NotNull @Unmodifiable List<@NotNull String> getPermissionCodes() {
        return List.copyOf(Objects.requireNonNull(permissionCodes, "权限编码不能为空"));
    }

    /** @return 已校验并规范化的新 ACTION 编码 */
    public @NotNull @Unmodifiable List<@NotNull TreeCode> getPermissionCodeTrees() {
        return getPermissionCodes().stream().map(TreeCode::ofColon).toList();
    }

    /**
     * 设置新 ACTION 编码集合。
     * @param permissionCodes 新权限编码集合
     */
    public void setPermissionCodes(@Nullable List<@NotNull String> permissionCodes) {
        this.permissionCodes = permissionCodes == null ? null : List.copyOf(permissionCodes);
    }
}
