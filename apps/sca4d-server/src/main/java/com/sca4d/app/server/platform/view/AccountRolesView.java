package com.sca4d.app.server.platform.view;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import com.sca4d.iam.rbac.PrincipalRoles;
import com.sca4d.iam.rbac.Role;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 返回 Platform Account 当前角色标识。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class AccountRolesView {

    private final long accountId;

    private final @NotNull List<@NotNull Long> roleIds;

    private final @NotNull Version revision;

    private AccountRolesView(
            long accountId,
            @NotNull List<@NotNull Long> roleIds,
            @NotNull Version revision) {
        this.accountId = accountId;
        this.roleIds = List.copyOf(roleIds);
        this.revision = revision;
    }

    /**
     * 从内部角色结果创建 HTTP 视图。
     *
     * @param accountId Account 标识
     * @param roles 内部 Principal 角色结果
     * @return HTTP Account 角色视图
     */
    @CheckReturnValue
    public static @NotNull AccountRolesView from(long accountId, @NotNull PrincipalRoles roles) {
        PrincipalRoles checked = Objects.requireNonNull(roles, "Account 角色不能为空");
        return new AccountRolesView(
                accountId,
                checked.roles().stream().map(Role::roleId).toList(),
                checked.version());
    }

    /**
     * 返回 Account 标识。
     *
     * @return Account 标识
     */
    public long getAccountId() {
        return accountId;
    }

    /**
     * 返回当前角色标识。
     *
     * @return 当前角色标识
     */
    public @NotNull @Unmodifiable List<@NotNull Long> getRoleIds() {
        return roleIds;
    }

    /** @return 生成当前 Account 角色集合的版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
