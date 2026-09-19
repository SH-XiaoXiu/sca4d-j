package com.sca4d.app.server.platform.view;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import com.sca4d.iam.base.rbac.PlatformRoleDetails;
import com.sca4d.iam.rbac.Role;
import com.sca4d.iam.rbac.RoleStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回 Platform 角色的公开字段。 */
@Schema(name = "PlatformRole")
@SuppressFBWarnings(value = "SS_SHOULD_BE_STATIC", justification = "@Localize requires an instance field for BaseViewSerializer metadata")
@SuppressWarnings({"FieldCanBeLocal", "unused"}) // Jackson 调用 getter，BaseViewSerializer 读取 @Localize 字段。
public final class RoleView extends BaseView {

    private final long id;

    private final @NotNull String code;

    @Localize(key = "name")
    private final @NotNull String name = "";

    private final @NotNull RoleStatus status;

    private final boolean builtIn;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    private final @NotNull Version revision;

    private RoleView(
            long id,
            @NotNull String code,
            @NotNull RoleStatus status,
            boolean builtIn,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull PlatformRoleDetails details) {
        super(details.i18n());
        this.id = id;
        this.code = code;
        this.status = status;
        this.builtIn = builtIn;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.revision = details.version();
    }

    /**
     * 从 RBAC 角色创建 HTTP 视图。
     *
     * @param details 当前 Platform 角色详情
     * @return HTTP 角色视图
     */
    @CheckReturnValue
    public static @NotNull RoleView from(@NotNull PlatformRoleDetails details) {
        PlatformRoleDetails checkedDetails = Objects.requireNonNull(details, "角色详情不能为空");
        Role checked = checkedDetails.role();
        return new RoleView(
                checked.roleId(),
                checked.code(),
                checked.status(),
                checked.builtIn(),
                checked.createdAt(),
                checked.updatedAt(),
                checkedDetails);
    }

    /**
     * 返回角色标识。
     * @return 角色标识
     */
    public long getId() {
        return id;
    }

    /**
     * 返回角色编码。
     * @return 角色编码
     */
    public @NotNull String getCode() {
        return code;
    }

    /**
     * 返回角色名称。
     * @return 角色名称
     */
    public @NotNull String getName() {
        return name;
    }

    /**
     * 返回角色状态。
     * @return 角色状态
     */
    public @NotNull RoleStatus getStatus() {
        return status;
    }

    /**
     * 返回是否为内置角色。
     * @return 是否为内置角色
     */
    public boolean isBuiltIn() {
        return builtIn;
    }

    /**
     * 返回创建时间。
     * @return 创建时间
     */
    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 返回最后修改时间。
     * @return 最后修改时间
     */
    public @NotNull Instant getUpdatedAt() {
        return updatedAt;
    }

    /** @return 生成当前完整角色表示的版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
