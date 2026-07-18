package com.sca4d.common.permission;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 权限节点类型。
 */
public enum PermissionType implements BaseEnum {
    /** 只用于组织权限目录的顶层模块，不能直接授予角色。 */
    MODULE("MODULE", "模块"),
    /** 模块中的资源分组，不能直接授予角色。 */
    RESOURCE("RESOURCE", "资源"),
    /** 可以直接授予角色或绑定菜单的具体操作。 */
    ACTION("ACTION", "动作");

    private final String value;
    private final String description;

    PermissionType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }

    /**
     * 当前节点是否可以直接授予角色或菜单。
     *
     * @return 可以直接授权时返回 true
     */
    public boolean grantable() {
        return this == ACTION;
    }
}
