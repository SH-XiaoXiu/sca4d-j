package com.sca4d.system.view;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.permission.PermissionCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 权限目录视图。
 */
@Getter
@AllArgsConstructor
@Schema(description = "权限目录视图")
public class PermissionCatalogView {

    /**
     * 权限树。
     */
    @Schema(description = "权限树")
    private final List<PermissionNodeView> tree;

    /**
     * 权限扁平列表。
     */
    @Schema(description = "权限扁平列表")
    private final List<PermissionItemView> items;

    /**
     * 权限节点总数。
     */
    @Schema(description = "权限节点总数", example = "12")
    private final int total;

    /**
     * 从权限目录构造视图。
     *
     * @param catalog 权限目录
     * @param messages 按当前请求语言解析消息键的实现
     * @return 权限目录视图
     */
    public static @NotNull PermissionCatalogView from(
            @NotNull PermissionCatalog catalog, @NotNull LocalizedMessages messages) {
        List<PermissionNodeView> tree = catalog.tree().stream().map(node -> PermissionNodeView.from(node, messages)).toList();
        List<PermissionItemView> items = catalog.items().stream().map(item -> PermissionItemView.from(item, messages)).toList();
        return new PermissionCatalogView(tree, items, items.size());
    }
}
