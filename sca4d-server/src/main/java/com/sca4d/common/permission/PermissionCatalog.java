package com.sca4d.common.permission;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限目录。
 *
 * <p>该目录是代码侧权限规范源，负责收集项目权限树并在启动阶段完成结构校验。
 */
public final class PermissionCatalog {

    private static final Comparator<PermissionNode> NODE_ORDER = Comparator.comparingInt(PermissionNode::getSortOrder)
            .thenComparing(PermissionNode::getCode);

    private final List<PermissionNode> tree;
    private final List<PermissionItem> items;

    /**
     * 从权限模块集合构建权限目录。
     *
    * @param modules 权限模块集合
     */
    public PermissionCatalog(@NotNull List<PermissionModule> modules) {
        List<PermissionNode> roots = new ArrayList<>();
        for (PermissionModule module : modules) {
            if (module == null) {
                throw new IllegalArgumentException("权限模块不能为空");
            }
            PermissionNode root = module.permissionTree();
            if (root == null) {
                throw new IllegalArgumentException("权限模块根节点不能为空: " + module.getClass().getName());
            }
            roots.add(root);
        }
        Map<String, PermissionItem> checkedItems = new LinkedHashMap<>();
        for (PermissionNode root : roots) {
            validate(root, null, checkedItems);
        }
        this.tree = roots.stream().map(this::sorted).sorted(NODE_ORDER).toList();
        List<PermissionItem> flattened = new ArrayList<>();
        for (PermissionNode root : tree) {
            flatten(root, null, flattened);
        }
        this.items = List.copyOf(flattened);
    }

    /**
     * 获取权限树。
     *
     * @return 权限树
     */
    public @NotNull List<PermissionNode> tree() {
        return tree;
    }

    /**
     * 获取权限扁平列表。
     *
     * @return 权限扁平列表
     */
    public @NotNull List<PermissionItem> items() {
        return items;
    }

    /**
     * 递归校验权限节点类型、父子编码、动作叶子约束和全目录编码唯一性。
     *
     * @param node 当前校验节点
     * @param parentCode 父节点编码；根节点为 {@code null}
     * @param checkedItems 已校验编码到扁平权限项的索引
     */
    private void validate(
            @NotNull PermissionNode node,
            @Nullable String parentCode,
            @NotNull Map<String, PermissionItem> checkedItems) {
        if (node.getCode() == null || node.getCode().isBlank()) {
            throw new IllegalArgumentException("权限编码不能为空");
        }
        if (node.getNameKey() == null || node.getNameKey().isBlank()) {
            throw new IllegalArgumentException("权限名称消息键不能为空: " + node.getCode());
        }
        if (node.getType() == null) {
            throw new IllegalArgumentException("权限节点类型不能为空: " + node.getCode());
        }
        if (parentCode == null && node.getType() != PermissionType.MODULE) {
            throw new IllegalArgumentException("权限根节点必须是模块: " + node.getCode());
        }
        if (parentCode != null
                && !node.getCode().startsWith(parentCode + ":")
                && !node.getCode().startsWith(parentCode + ".")) {
            throw new IllegalArgumentException("子权限编码必须以父权限编码为前缀: " + parentCode + " -> " + node.getCode());
        }
        if (node.getType() == PermissionType.ACTION && !node.getChildren().isEmpty()) {
            throw new IllegalArgumentException("动作权限不能包含子节点: " + node.getCode());
        }
        PermissionItem existing = checkedItems.putIfAbsent(node.getCode(),
                new PermissionItem(node.getCode(), parentCode, node.getNameKey(), node.getType(), node.getSortOrder()));
        if (existing != null) {
            throw new IllegalArgumentException("重复权限编码: " + node.getCode());
        }
        for (PermissionNode child : node.getChildren()) {
            validate(child, node.getCode(), checkedItems);
        }
    }

    /**
     * 递归复制权限树并按 sortOrder、code 固定排序每级子节点。
     *
     * @param node 待排序权限节点
     * @return 不复用原 children 集合的排序节点
     */
    private @NotNull PermissionNode sorted(@NotNull PermissionNode node) {
        List<PermissionNode> children = node.getChildren().stream().map(this::sorted).sorted(NODE_ORDER).toList();
        return new PermissionNode(node.getCode(), node.getNameKey(), node.getType(), node.getSortOrder(), children);
    }

    /**
     * 按已排序树的先序顺序生成权限扁平列表。
     *
     * @param node 当前展开节点
     * @param parentCode 父节点编码；根节点为 {@code null}
     * @param flattened 接收权限项的有序列表
     */
    private void flatten(
            @NotNull PermissionNode node, @Nullable String parentCode, @NotNull List<PermissionItem> flattened) {
        flattened.add(new PermissionItem(node.getCode(), parentCode, node.getNameKey(), node.getType(), node.getSortOrder()));
        for (PermissionNode child : node.getChildren()) {
            flatten(child, node.getCode(), flattened);
        }
    }
}
