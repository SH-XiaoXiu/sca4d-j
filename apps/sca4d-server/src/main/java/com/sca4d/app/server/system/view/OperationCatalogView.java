package com.sca4d.app.server.system.view;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.core.api.permission.OperationNodeType;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 返回完整示例 App 的 Operation 目录树、扁平 ACTION 列表和总数。 */
@Schema(name = "PermissionCatalog")
@SuppressWarnings("unused") // Jackson 与 BaseView 通过 getter 和 @Localize source 读取响应字段。
public class OperationCatalogView {

    private final @NotNull List<@NotNull Node> tree;

    private final @NotNull List<@NotNull Node> items;

    private final int total;

    private OperationCatalogView(
            @NotNull List<@NotNull Node> tree,
            @NotNull List<@NotNull Node> items) {
        this.tree = List.copyOf(tree);
        this.items = List.copyOf(items);
        this.total = items.size();
    }

    /**
     * 从 Operation 目录创建 HTTP 投影。
     *
     * @param catalog 已完成校验和排序的 Operation 目录
     * @return HTTP 目录投影
     */
    @CheckReturnValue
    public static @NotNull OperationCatalogView from(@NotNull OperationCatalog catalog) {
        OperationCatalog checked = Objects.requireNonNull(catalog, "Operation 目录不能为空");
        List<@NotNull Node> tree = checked.tree()
                .stream()
                .map(node -> Node.from(node, null))
                .toList();
        List<@NotNull Node> items = checked.actions().stream().map(Node::from).toList();
        return new OperationCatalogView(tree, items);
    }

    /**
     * 返回 Operation 目录树。
     * @return Operation 目录树
     */
    public @NotNull @Unmodifiable List<@NotNull Node> getTree() {
        return tree;
    }

    /**
     * 返回可授予的扁平 ACTION 列表。
     * @return 可授予的扁平 ACTION 列表
     */
    public @NotNull @Unmodifiable List<@NotNull Node> getItems() {
        return items;
    }

    /**
     * 返回可授予 ACTION 总数。
     * @return 可授予 ACTION 总数
     */
    public int getTotal() {
        return total;
    }

    /** Operation 目录中的树节点或扁平 ACTION。 */
    public static class Node extends BaseView {

        private final @NotNull String code;

        @JsonInclude(JsonInclude.Include.ALWAYS)
        @JsonProperty("parentCode")
        private final @Nullable String parentCode;

        private final @NotNull String nameKey;

        @Localize(source = "nameText")
        private final @NotNull String name;

        private final @NotNull OperationNodeType type;

        private final boolean grantable;

        private final int sortOrder;

        private final @NotNull List<@NotNull Node> children;

        private Node(
                @NotNull String code,
                @Nullable String parentCode,
                @NotNull String nameKey,
                @NotNull OperationNodeType type,
                int sortOrder,
                @NotNull List<@NotNull Node> children) {
            this.code = code;
            this.parentCode = parentCode;
            this.nameKey = nameKey;
            this.name = nameKey;
            this.type = type;
            this.grantable = type.grantable();
            this.sortOrder = sortOrder;
            this.children = List.copyOf(children);
        }

        private static @NotNull Node from(@NotNull OperationNode node, @Nullable TreeCode parentCode) {
            List<@NotNull Node> children = node.children()
                    .stream()
                    .map(child -> from(child, node.code()))
                    .toList();
            return new Node(
                    node.code().format(COLON),
                    parentCode == null ? null : parentCode.format(COLON),
                    node.nameKey(),
                    node.type(),
                    node.sortOrder(),
                    children);
        }

        private static @NotNull Node from(@NotNull OperationNode.OperationAction item) {
            TreeCode parentCode = item.parentCode();
            return new Node(
                    item.code().format(COLON),
                    parentCode == null ? null : parentCode.format(COLON),
                    item.nameKey(),
                    OperationNodeType.ACTION,
                    item.sortOrder(),
                    List.of());
        }

        /**
         * 返回 Operation 编码。
         * @return Operation 编码
         */
        public @NotNull String getCode() {
            return code;
        }

        /**
         * 返回父 Operation 编码。
         * @return 父 Operation 编码；根节点没有父编码
         */
        public @Nullable String getParentCode() {
            return parentCode;
        }

        /**
         * 返回 Operation 名称消息键。
         * @return Operation 名称消息键
         */
        public @NotNull String getNameKey() {
            return nameKey;
        }

        /**
         * 返回按当前请求语言解析的名称。
         * @return 按当前请求语言解析的名称
         */
        public @NotNull String getName() {
            return name;
        }

        /**
         * 返回名称的可本地化文本。
         * @return 名称的可本地化文本
         */
        @SuppressWarnings("ProtectedMemberInFinalClass") // @Localize 要求 source 是 protected 方法。
        protected @NotNull LocalizableText nameText() {
            return MessageText.of(nameKey);
        }

        /**
         * 返回节点类型。
         * @return 节点类型
         */
        public @NotNull OperationNodeType getType() {
            return type;
        }

        /**
         * 返回是否可以授予角色。
         * @return 是否可以授予角色
         */
        public boolean isGrantable() {
            return grantable;
        }

        /**
         * 返回同级排序值。
         * @return 同级排序值
         */
        public int getSortOrder() {
            return sortOrder;
        }

        /**
         * 返回子节点。
         * @return 子节点
         */
        public @NotNull @Unmodifiable List<@NotNull Node> getChildren() {
            return children;
        }
    }
}
