package com.sca4d.app.server.platform.view;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.iam.base.rbac.PlatformRoleDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回 Platform 角色编辑使用的完整多语言字段。 */
@Schema(name = "PlatformRoleI18n")
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class RoleI18nView {

    private final @NotNull LocalizedFields i18n;

    private final @NotNull Version revision;

    private final long roleId;

    private RoleI18nView(long roleId, @NotNull LocalizedFields i18n, @NotNull Version revision) {
        this.roleId = roleId;
        this.i18n = i18n;
        this.revision = revision;
    }

    /**
     * 从 Platform 角色详情创建编辑视图。
     *
     * @param details 当前 Platform 角色详情
     * @return 包含完整语言映射的编辑视图
     */
    @CheckReturnValue
    public static @NotNull RoleI18nView from(@NotNull PlatformRoleDetails details) {
        PlatformRoleDetails checked = Objects.requireNonNull(details, "角色详情不能为空");
        return new RoleI18nView(checked.role().roleId(), checked.i18n(), checked.version());
    }

    /**
     * 返回角色标识。
     * @return 角色标识
     */
    public long getRoleId() {
        return roleId;
    }

    /**
     * 返回角色保存的完整多语言字段。
     * @return 完整多语言字段
     */
    public @NotNull LocalizedFields getI18n() {
        return i18n;
    }

    /** @return 生成当前完整多语言字段的角色版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
