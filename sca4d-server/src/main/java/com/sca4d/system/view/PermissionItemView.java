package com.sca4d.system.view;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.permission.PermissionItem;
import com.sca4d.common.permission.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * 权限目录扁平项视图。
 *
 * <p>{@code code} 与 {@code parentCode} 均来自业务模块代码权限目录，不在展示层生成。
 */
@Getter
@AllArgsConstructor
@Schema(description = "权限目录扁平项视图")
public class PermissionItemView {

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
     * 从权限目录项构造视图。
     *
     * @param item 权限目录项
     * @param messages 按当前请求语言解析消息键的实现
     * @return 权限目录扁平项视图
     */
    public static @NotNull PermissionItemView from(
            @NotNull PermissionItem item, @NotNull LocalizedMessages messages) {
        return new PermissionItemView(
                item.getCode(),
                item.getParentCode(),
                item.getNameKey(),
                messages.get(item.getNameKey()),
                item.getType(),
                item.getType().grantable(),
                item.getSortOrder());
    }
}
