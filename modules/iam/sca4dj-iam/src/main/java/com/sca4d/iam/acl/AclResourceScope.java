package com.sca4d.iam.acl;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.ResourceScope;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/**
 * ACL 对集合资源允许访问的资源标识范围。
 */
@SuppressWarnings({"ClassCanBeRecord", "unused"})
public class AclResourceScope implements ResourceScope {
    private final @NotNull TreeCode resourceType;

    private final @NotNull @Unmodifiable List<@NotNull String> resourceIds;

    /**
     * 创建集合资源的可访问实例范围。
     *
     * @param resourceType 资源类型
     * @param resourceIds  可访问的资源标识
     */
    public AclResourceScope(
            @NotNull TreeCode resourceType,
            @NotNull List<@NotNull String> resourceIds) {
        this.resourceType = Objects.requireNonNull(resourceType, "资源类型不能为空");
        this.resourceIds = List.copyOf(resourceIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Contract(pure = true)
    public @NotNull TreeCode resourceType() {
        return resourceType;
    }

    /**
     * 返回集合权限允许访问的实例。
     *
     * @return 不可修改的可访问资源标识列表
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable List<@NotNull String> resourceIds() {
        return resourceIds;
    }
}
