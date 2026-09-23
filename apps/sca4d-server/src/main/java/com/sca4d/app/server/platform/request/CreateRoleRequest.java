package com.sca4d.app.server.platform.request;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.validation.Req;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 接收完整示例 App 创建 Platform 角色及初始 ACTION 的请求。 */
@Schema(name = "CreatePlatformRoleRequest")
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class CreateRoleRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String code;

    @Req.NotNull
    private @Nullable LocalizedFields i18n;

    @Req.NotNull
    private @Nullable List<@NotBlank @NotNull String> permissionCodes = new ArrayList<>();

    /** 创建供 JSON 反序列化填充的空请求。 */
    public CreateRoleRequest() {
    }

    /**
     * 返回角色编码。
     * @return 角色编码
     */
    public @NotNull String getCode() {
        return Objects.requireNonNull(code, "角色编码不能为空");
    }

    /**
     * 设置角色编码。
     * @param code 角色编码
     */
    public void setCode(@Nullable String code) {
        this.code = code;
    }

    /**
     * 返回角色完整多语言字段。
     * @return 完整多语言字段
     */
    public @NotNull LocalizedFields getI18n() {
        return Objects.requireNonNull(i18n, "角色多语言字段不能为空");
    }

    /**
     * 设置角色完整多语言字段。
     * @param i18n 完整多语言字段
     */
    public void setI18n(@Nullable LocalizedFields i18n) {
        this.i18n = i18n;
    }

    /**
     * 返回初始 ACTION 编码集合。
     * @return 初始 ACTION 编码集合
     */
    public @NotNull @Unmodifiable List<@NotNull String> getPermissionCodes() {
        return List.copyOf(Objects.requireNonNull(permissionCodes, "权限编码不能为空"));
    }

    /** @return 已校验并规范化的初始 ACTION 编码 */
    public @NotNull @Unmodifiable List<@NotNull TreeCode> getPermissionCodeTrees() {
        return getPermissionCodes().stream().map(TreeCode::ofColon).toList();
    }

    /**
     * 设置初始 ACTION 编码集合。
     * @param permissionCodes 初始权限编码集合
     */
    public void setPermissionCodes(@Nullable List<@NotNull String> permissionCodes) {
        this.permissionCodes = permissionCodes == null ? null : List.copyOf(permissionCodes);
    }
}
