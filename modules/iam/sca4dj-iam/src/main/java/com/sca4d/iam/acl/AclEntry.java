package com.sca4d.iam.acl;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 一条直接授予业务主体的资源动作权限。
 */
@SuppressWarnings("ClassCanBeRecord")
public final class AclEntry {
    private final @NotNull Long principalId;

    private final @NotNull TreeCode resourceType;

    private final @NotNull String resourceId;

    private final @NotNull Action action;

    /**
     * 创建一条 ACL 授权事实。
     *
     * @param principalId  被授权业务主体
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param action       被允许动作
     */
    public AclEntry(
            @NotNull Long principalId,
            @NotNull TreeCode resourceType,
            @NotNull String resourceId,
            @NotNull Action action) {
        this.principalId = Objects.requireNonNull(principalId, "业务主体标识不能为空");
        this.resourceType = Objects.requireNonNull(resourceType, "资源类型不能为空");
        this.resourceId = TextUtils.requireExactText(resourceId, "资源标识");
        this.action = Objects.requireNonNull(action, "动作不能为空");
    }

    /**
     * 返回被授权业务主体标识。
     *
     * @return 业务主体标识
     */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /**
     * 返回资源类型。
     *
     * @return 资源类型
     */
    @Contract(pure = true)
    public @NotNull TreeCode resourceType() {
        return resourceType;
    }

    /**
     * 返回资源标识。
     *
     * @return 资源标识
     */
    @Contract(pure = true)
    public @NotNull String resourceId() {
        return resourceId;
    }

    /**
     * 返回被允许动作。
     *
     * @return 动作
     */
    @Contract(pure = true)
    public @NotNull Action action() {
        return action;
    }

}
