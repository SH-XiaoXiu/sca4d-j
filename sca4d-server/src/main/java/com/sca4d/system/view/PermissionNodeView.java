package com.sca4d.system.view;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.permission.PermissionNode;
import com.sca4d.common.permission.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 权限树节点视图。
 *
 * <p>{@code code} 与 {@code parentCode} 均来自业务模块代码权限目录，不接受客户端或数据库自由扩展。
 */
@Getter
@AllArgsConstructor
@Schema(description = "权限树节点视图")
public class PermissionNodeView {

    /**
     * 权限编码。
     */
    @Schema(description = "权限编码", example = "system:operation-logs:read")
    private final String code;

    /**
     * 父权限编码。
     */
    @Schema(description = "父权限编码", example = "system:operation-logs")
    private final String parentCode;

    /** 权限名称固定消息键。 */
    @Schema(description = "权限名称消息键", example = "permission.system.operation-logs.read")
    private final String nameKey;

    /**
     * 权限名称。
     */
    @Schema(description = "权限名称", example = "查看操作日志")
    private final String name;

    /**
     * 权限节点类型。
     */
    @Schema(description = "权限节点类型", example = "ACTION")
    private final PermissionType type;

    /**
     * 是否可以直接授权。
     */
    @Schema(description = "是否可以直接授权", example = "true")
    private final boolean grantable;

    /**
     * 排序值。
     */
    @Schema(description = "排序值", example = "100")
    private final int sortOrder;

    /**
     * 子权限节点。
     */
    @Schema(description = "子权限节点")
    private final List<PermissionNodeView> children;

    /**
     * 从权限树节点构造根视图。
     *
     * @param node 权限树节点
     * @param messages 按当前请求语言解析消息键的实现
     * @return 权限树节点视图
     */
    public static @NotNull PermissionNodeView from(
            @NotNull PermissionNode node, @NotNull LocalizedMessages messages) {
        return from(node, null, messages);
    }

    /**
     * 递归构造带父编码和本地化名称的权限树视图。
     *
     * @param node 当前权限树节点
     * @param parentCode 父权限编码；根节点为 {@code null}
     * @param messages 按当前请求语言解析消息键的实现
     * @return 当前节点及其完整子树视图
     */
    private static @NotNull PermissionNodeView from(
            @NotNull PermissionNode node,
            @Nullable String parentCode,
            @NotNull LocalizedMessages messages) {
        return new PermissionNodeView(
                node.getCode(),
                parentCode,
                node.getNameKey(),
                messages.get(node.getNameKey()),
                node.getType(),
                node.getType().grantable(),
                node.getSortOrder(),
                node.getChildren().stream().map(child -> from(child, node.getCode(), messages)).toList());
    }
}
