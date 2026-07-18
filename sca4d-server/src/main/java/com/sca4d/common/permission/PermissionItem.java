package com.sca4d.common.permission;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 权限目录扁平项。
 *
 * <p>权限编码由业务模块的代码目录扩展，父子关系随目录一起同步，不接受数据库侧自由创建未知编码。
 */
@Getter
public final class PermissionItem {

    private final String code;
    private final String parentCode;
    private final String nameKey;
    private final PermissionType type;
    private final int sortOrder;

    /**
     * 创建权限目录扁平项。
     *
     * @param code 业务模块代码目录定义的权限编码
     * @param parentCode 父权限编码；根节点为 null
     * @param nameKey 权限名称消息键
     * @param type 权限节点类型
     * @param sortOrder 排序值
     */
    public PermissionItem(
            @NotNull String code,
            @Nullable String parentCode,
            @NotNull String nameKey,
            @NotNull PermissionType type,
            int sortOrder) {
        this.code = code;
        this.parentCode = parentCode;
        this.nameKey = nameKey;
        this.type = type;
        this.sortOrder = sortOrder;
    }
}
