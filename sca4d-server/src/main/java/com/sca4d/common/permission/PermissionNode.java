package com.sca4d.common.permission;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * 权限树节点。
 *
 * <p>{@code code} 来自各业务模块的代码权限目录，并由 Bootstrap 同步到数据库；它是受目录约束的开放编码，
 * 不是运行时任意字符串。
 */
@Getter
public final class PermissionNode {

    private final String code;
    private final String nameKey;
    private final PermissionType type;
    private final int sortOrder;
    private final List<PermissionNode> children;

    /**
     * 创建权限树节点。
     *
     * @param code 业务模块代码目录定义的权限编码
     * @param nameKey 权限名称消息键
     * @param type 权限节点类型
     * @param sortOrder 排序值
     * @param children 子节点
     */
    public PermissionNode(
            @NotNull String code,
            @NotNull String nameKey,
            @NotNull PermissionType type,
            int sortOrder,
            @NotNull List<PermissionNode> children) {
        this.code = code;
        this.nameKey = nameKey;
        this.type = type;
        this.sortOrder = sortOrder;
        this.children = List.copyOf(children);
    }

    /**
     * 创建模块节点。
     *
     * @param code 模块编码
     * @param nameKey 模块名称消息键
     * @param sortOrder 排序值
     * @param children 子节点
     * @return 模块节点
     */
    public static @NotNull PermissionNode module(
            @NotNull String code, @NotNull String nameKey, int sortOrder, @NotNull PermissionNode... children) {
        return new PermissionNode(code, nameKey, PermissionType.MODULE, sortOrder, Arrays.asList(children));
    }

    /**
     * 创建资源节点。
     *
     * @param code 资源编码
     * @param nameKey 资源名称消息键
     * @param sortOrder 排序值
     * @param children 子节点
     * @return 资源节点
     */
    public static @NotNull PermissionNode resource(
            @NotNull String code, @NotNull String nameKey, int sortOrder, @NotNull PermissionNode... children) {
        return new PermissionNode(code, nameKey, PermissionType.RESOURCE, sortOrder, Arrays.asList(children));
    }

    /**
     * 创建动作节点。
     *
     * @param code 动作编码
     * @param nameKey 动作名称消息键
     * @param sortOrder 排序值
     * @return 动作节点
     */
    public static @NotNull PermissionNode action(@NotNull String code, @NotNull String nameKey, int sortOrder) {
        return new PermissionNode(code, nameKey, PermissionType.ACTION, sortOrder, List.of());
    }
}
