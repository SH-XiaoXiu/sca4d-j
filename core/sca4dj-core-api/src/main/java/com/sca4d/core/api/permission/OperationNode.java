package com.sca4d.core.api.permission;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** 表示模块、业务资源或可授予 Operation 构成的固定三层目录节点。 */
public abstract class OperationNode {

    private static final @NotNull Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_]+|\\*");

    private static final @NotNull Comparator<@NotNull OperationNode> NODE_ORDER = Comparator
            .comparingInt(OperationNode::sortOrder)
            .thenComparing(OperationNode::code);

    private final @NotNull String code;

    private final @Nullable String parentCode;

    private final @NotNull String nameKey;

    private final int sortOrder;

    private OperationNode(
            @NotNull String code,
            @Nullable String parentCode,
            @NotNull String nameKey,
            int sortOrder) {
        this.code = normalizeNodeCode(code);
        this.parentCode = parentCode == null ? null : normalizeNodeCode(parentCode);
        this.nameKey = TextUtils.requireText(nameKey, "Operation 节点名称消息键");
        this.sortOrder = sortOrder;
    }

    /**
     * 使用模块锚点展开全部局部资源段和动作段。
     *
     * @param anchor 模块唯一编码和完整 Operation 前缀
     * @param nameKey 模块名称消息键
     * @param sortOrder 模块排序值
     * @param resources 局部 RESOURCE 声明
     * @return 只包含完整编码节点的 MODULE 聚合
     */
    @CheckReturnValue
    @Contract("_, _, _, _ -> new")
    public static @NotNull Module module(
            @NotNull String anchor,
            @NotNull String nameKey,
            int sortOrder,
            @NotNull ResourceDeclaration @NotNull... resources) {
        String checkedAnchor = requireSegment(anchor, "Operation 模块锚点", false);
        List<Resource> children = new ArrayList<>();
        LinkedHashSet<String> resourceCodes = new LinkedHashSet<>();
        for (ResourceDeclaration declaration : resources) {
            ResourceDeclaration checked = Objects.requireNonNull(declaration, "Operation RESOURCE 声明不能为空");
            String resourceCode = checkedAnchor + ':' + checked.segment;
            if (!resourceCodes.add(resourceCode)) {
                throw new IllegalArgumentException("Operation RESOURCE 编码重复: " + resourceCode);
            }
            List<OperationAction> actions = new ArrayList<>();
            LinkedHashSet<String> actionCodes = new LinkedHashSet<>();
            for (ActionDeclaration action : checked.actions) {
                String actionCode = resourceCode + ':' + action.segment;
                if (!actionCodes.add(actionCode)) {
                    throw new IllegalArgumentException("Operation ACTION 编码重复: " + actionCode);
                }
                actions.add(
                        new OperationAction(
                                new Operation(actionCode),
                                resourceCode,
                                action.nameKey,
                                action.sortOrder));
            }
            children.add(
                    new Resource(
                            resourceCode,
                            checkedAnchor,
                            checked.nameKey,
                            checked.sortOrder,
                            actions));
        }
        return new Module(checkedAnchor, nameKey, sortOrder, children);
    }

    /**
     * 声明 MODULE 下的一个局部资源段。
     *
     * @param segment 不包含模块锚点的资源段
     * @param nameKey 资源名称消息键
     * @param sortOrder 资源排序值
     * @param actions 局部 ACTION 声明
     * @return 只能传给 {@link #module(String, String, int, ResourceDeclaration...)} 的资源声明
     */
    @CheckReturnValue
    @Contract("_, _, _, _ -> new")
    public static @NotNull ResourceDeclaration resource(
            @NotNull String segment,
            @NotNull String nameKey,
            int sortOrder,
            @NotNull ActionDeclaration @NotNull... actions) {
        return new ResourceDeclaration(segment, nameKey, sortOrder, Arrays.asList(actions));
    }

    /**
     * 声明 RESOURCE 下的一个局部动作段。
     *
     * @param segment 不包含模块和资源前缀的动作段
     * @param nameKey 动作名称消息键
     * @param sortOrder 动作排序值
     * @return 只能传给 {@link #resource(String, String, int, ActionDeclaration...)} 的动作声明
     */
    @CheckReturnValue
    @Contract("_, _, _ -> new")
    public static @NotNull ActionDeclaration action(
            @NotNull String segment,
            @NotNull String nameKey,
            int sortOrder) {
        return new ActionDeclaration(segment, nameKey, sortOrder);
    }

    /** @return 节点的完整编码 */
    public final @NotNull String code() {
        return code;
    }

    /** @return 父节点完整编码；MODULE 返回空 */
    public final @Nullable String parentCode() {
        return parentCode;
    }

    /** @return 节点名称消息键 */
    public final @NotNull String nameKey() {
        return nameKey;
    }

    /** @return 节点在同级目录中的排序值 */
    public final int sortOrder() {
        return sortOrder;
    }

    /** @return 节点类型 */
    public abstract @NotNull OperationNodeType type();

    /** @return 类型正确且不可变的直接子节点 */
    public abstract @NotNull @Unmodifiable List<? extends @NotNull OperationNode> children();

    /** 校验模块、资源或动作的单个编码段。 */
    private static @NotNull String requireSegment(
            @NotNull String value,
            @NotNull String name,
            boolean wildcardAllowed) {
        String checked = TextUtils.requireText(value, name).toUpperCase(Locale.ROOT);
        if (!SEGMENT.matcher(checked).matches() || !wildcardAllowed && "*".equals(checked)) {
            throw new IllegalArgumentException(name + "只能包含字母、数字和下划线: " + checked);
        }
        return checked;
    }

    /** 规范化目录节点，允许 MODULE 根节点只有一个局部段。 */
    private static @NotNull String normalizeNodeCode(@NotNull String value) {
        String checked = TextUtils.requireText(value, "Operation 节点编码");
        if (checked.indexOf(':') < 0 && checked.indexOf('/') < 0 && checked.indexOf('*') < 0) {
            return requireSegment(checked, "Operation 节点编码", false);
        }
        return Operation.normalize(checked);
    }

    /** MODULE 工厂使用的局部 RESOURCE 声明，不参与最终目录。 */
    public static final class ResourceDeclaration {

        private final @NotNull String segment;

        private final @NotNull String nameKey;

        private final int sortOrder;

        private final @NotNull List<@NotNull ActionDeclaration> actions;

        private ResourceDeclaration(
                @NotNull String segment,
                @NotNull String nameKey,
                int sortOrder,
                @NotNull List<@NotNull ActionDeclaration> actions) {
            this.segment = requireSegment(segment, "Operation RESOURCE 局部编码", false);
            this.nameKey = TextUtils.requireText(nameKey, "Operation RESOURCE 名称消息键");
            this.sortOrder = sortOrder;
            this.actions = List.copyOf(actions);
        }
    }

    /** RESOURCE 工厂使用的局部 ACTION 声明，不参与最终目录。 */
    public static final class ActionDeclaration {

        private final @NotNull String segment;

        private final @NotNull String nameKey;

        private final int sortOrder;

        private ActionDeclaration(@NotNull String segment, @NotNull String nameKey, int sortOrder) {
            this.segment = requireSegment(segment, "Operation ACTION 局部编码", true);
            this.nameKey = TextUtils.requireText(nameKey, "Operation ACTION 名称消息键");
            this.sortOrder = sortOrder;
        }
    }

    /** 表示一个业务模块及其完整 RESOURCE 目录。 */
    public static final class Module extends OperationNode {

        private final @NotNull @Unmodifiable List<@NotNull Resource> resources;

        private Module(
                @NotNull String code,
                @NotNull String nameKey,
                int sortOrder,
                @NotNull List<@NotNull Resource> resources) {
            super(code, null, nameKey, sortOrder);
            this.resources = List.copyOf(resources);
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull OperationNodeType type() {
            return OperationNodeType.MODULE;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull @Unmodifiable List<@NotNull Resource> children() {
            return resources;
        }

        /** @return 按节点排序规则复制的 MODULE */
        @CheckReturnValue
        Module sorted() {
            return new Module(
                    code(),
                    nameKey(),
                    sortOrder(),
                    resources.stream().map(Resource::sorted).sorted(NODE_ORDER).toList());
        }

        /** @return 只保留指定 Operation 的 MODULE；没有匹配 ACTION 时为空 */
        @CheckReturnValue
        @NotNull
        Optional<@NotNull Module> retaining(@NotNull Set<@NotNull Operation> operations) {
            List<Resource> retained = resources.stream()
                    .map(resource -> resource.retaining(operations))
                    .flatMap(Optional::stream)
                    .toList();
            return retained.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Module(code(), nameKey(), sortOrder(), retained));
        }
    }

    /** 表示一个 MODULE 内的业务资源分组及其完整 ACTION 目录。 */
    public static final class Resource extends OperationNode {

        private final @NotNull @Unmodifiable List<@NotNull OperationAction> actions;

        private Resource(
                @NotNull String code,
                @NotNull String parentCode,
                @NotNull String nameKey,
                int sortOrder,
                @NotNull List<@NotNull OperationAction> actions) {
            super(code, parentCode, nameKey, sortOrder);
            this.actions = List.copyOf(actions);
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull OperationNodeType type() {
            return OperationNodeType.RESOURCE;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull @Unmodifiable List<@NotNull OperationAction> children() {
            return actions;
        }

        /** @return 按节点排序规则复制的 RESOURCE */
        @CheckReturnValue
        Resource sorted() {
            return new Resource(
                    code(),
                    Objects.requireNonNull(parentCode()),
                    nameKey(),
                    sortOrder(),
                    actions.stream().sorted(NODE_ORDER).toList());
        }

        /** @return 只保留指定 Operation 的 RESOURCE；没有匹配 ACTION 时为空 */
        @CheckReturnValue
        @NotNull
        Optional<@NotNull Resource> retaining(@NotNull Set<@NotNull Operation> operations) {
            List<OperationAction> retained = actions.stream()
                    .filter(action -> operations.contains(action.operation()))
                    .toList();
            return retained.isEmpty()
                    ? Optional.empty()
                    : Optional.of(
                            new Resource(
                                    code(),
                                    Objects.requireNonNull(parentCode()),
                                    nameKey(),
                                    sortOrder(),
                                    retained));
        }
    }

    /** 表示目录中可以直接授予 Principal 的固定 Operation。 */
    public static final class OperationAction extends OperationNode {

        private final @NotNull Operation operation;

        private OperationAction(
                @NotNull Operation operation,
                @NotNull String parentCode,
                @NotNull String nameKey,
                int sortOrder) {
            super(operation.value(), parentCode, nameKey, sortOrder);
            this.operation = operation;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull OperationNodeType type() {
            return OperationNodeType.ACTION;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull @Unmodifiable List<@NotNull OperationNode> children() {
            return List.of();
        }

        /** @return 可以装入 Session 或由权限策略判断的 Operation */
        public @NotNull Operation operation() {
            return operation;
        }
    }
}
