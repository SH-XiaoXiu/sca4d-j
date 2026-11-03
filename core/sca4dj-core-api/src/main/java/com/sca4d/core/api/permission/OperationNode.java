package com.sca4d.core.api.permission;

import com.sca4d.core.api.name.Display;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.AttributeAxis;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表示 Operation 目录中的一个节点：包含子节点的 {@link Group}，或可以授予 Principal 的 {@link Leaf}。
 *
 * <p>目录深度不限。分组可以同时包含子分组和叶子，例如 {@code ORDERS} 下既有分组 {@code ORDERS:INVOICES}，
 * 也可以直接有叶子 {@code ORDERS:EXPORT}。叶子编码的最后一段是动词（{@link Action}），例如
 * {@code ORDERS:INVOICES:READ} 的动词是 {@code READ}。编码总长度受 {@link OperationCode} 的 128 字符限制。
 *
 * <p>目录通过 {@link #root(TreeCode, int, Declaration...)} 声明。声明里只写相对父分组的局部段，
 * 完整编码由父分组编码依次追加得到，调用方不需要手写完整编码。
 */
public abstract class OperationNode {

    /** 按编码推导 {@link Display} 时使用的名称消息键前缀。 */
    private static final @NotNull String DISPLAY_PREFIX = "operation";

    private static final @NotNull Comparator<@NotNull OperationNode> NODE_ORDER = Comparator
            .comparingInt(OperationNode::sortOrder)
            .thenComparing(OperationNode::code);

    private final @NotNull Display display;

    private final @NotNull TreeCode code;

    private final @Nullable TreeCode parentCode;

    private final int sortOrder;

    private OperationNode(
            @NotNull TreeCode code,
            @Nullable TreeCode parentCode,
            @NotNull Display display,
            int sortOrder) {
        this.code = Objects.requireNonNull(code, "Operation 节点编码不能为空");
        this.parentCode = parentCode;
        this.display = Objects.requireNonNull(display, "Operation 节点显示不能为空");
        this.sortOrder = sortOrder;
    }

    /**
     * 使用锚点展开整棵目录，锚点是目录根分组的编码，也是其下全部编码的前缀。
     *
     * <p>根分组的 {@link Display} 按 {@link #DISPLAY_PREFIX} 和锚点推导。
     *
     * @param anchor    根分组的单节点编码，例如 {@code ORDERS}，不能是通配符 {@code *}
     * @param sortOrder 根分组在 {@link OperationCatalog} 根分组之间的排序值
     * @param children  根分组的直接子声明，可以是分组、叶子或两者混合；没有子声明时得到空分组
     * @return 已经展开完整编码的根分组，{@link Group#parentCode()} 为空
     * @throws IllegalArgumentException 锚点不是单节点、锚点是通配符，或同一父分组下有两个子节点的完整编码相同时抛出
     * @throws NullPointerException     任一子声明为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _, _ -> new")
    public static @NotNull Group root(
            @NotNull TreeCode anchor,
            int sortOrder,
            @NotNull Declaration @NotNull... children) {
        return root(anchor, null, sortOrder, children);
    }

    /**
     * 使用锚点展开整棵目录，并为根分组指定与编码推导结果不同的 {@link Display}。
     *
     * @param anchor    根分组的单节点编码，例如 {@code ORDERS}，不能是通配符 {@code *}
     * @param display   根分组显示；为 {@code null} 时按编码推导
     * @param sortOrder 根分组在 {@link OperationCatalog} 根分组之间的排序值
     * @param children  根分组的直接子声明，可以是分组、叶子或两者混合；没有子声明时得到空分组
     * @return 已经展开完整编码的根分组，{@link Group#parentCode()} 为空
     * @throws IllegalArgumentException 锚点不是单节点、锚点是通配符，或同一父分组下有两个子节点的完整编码相同时抛出
     * @throws NullPointerException     任一子声明为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _, _, _ -> new")
    public static @NotNull Group root(
            @NotNull TreeCode anchor,
            @Nullable Display display,
            int sortOrder,
            @NotNull Declaration @NotNull... children) {
        TreeCode anchorCode = requireSegment(anchor, "Operation 根分组锚点", false);
        return new Group(
                anchorCode,
                null,
                display == null ? Display.of(DISPLAY_PREFIX, anchorCode) : display,
                sortOrder,
                build(anchorCode, Arrays.asList(children)));
    }

    /**
     * 声明父分组下的一个子分组，{@link Display} 按完整编码推导。
     *
     * @param segment   不含父分组前缀的单节点编码，例如 {@code INVOICES}，不能是通配符 {@code *}
     * @param sortOrder 分组在同一父分组的子节点中的排序值
     * @param children  子分组的直接子声明，可以是分组、叶子或两者混合
     * @return 只能传给 {@link #root(TreeCode, int, Declaration...)} 或另一个分组的声明
     * @throws IllegalArgumentException segment 不是单节点或是通配符时抛出
     * @throws NullPointerException     任一子声明为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _, _ -> new")
    public static @NotNull Declaration group(
            @NotNull TreeCode segment,
            int sortOrder,
            @NotNull Declaration @NotNull... children) {
        return new GroupDeclaration(segment, null, sortOrder, Arrays.asList(children));
    }

    /**
     * 声明父分组下的一个子分组，并指定与编码推导结果不同的 {@link Display}。
     *
     * @param segment   不含父分组前缀的单节点编码，例如 {@code INVOICES}，不能是通配符 {@code *}
     * @param display   分组显示
     * @param sortOrder 分组在同一父分组的子节点中的排序值
     * @param children  子分组的直接子声明，可以是分组、叶子或两者混合
     * @return 只能传给 {@link #root(TreeCode, int, Declaration...)} 或另一个分组的声明
     * @throws IllegalArgumentException segment 不是单节点或是通配符时抛出
     * @throws NullPointerException     显示或任一子声明为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _, _, _ -> new")
    public static @NotNull Declaration group(
            @NotNull TreeCode segment,
            @NotNull Display display,
            int sortOrder,
            @NotNull Declaration @NotNull... children) {
        return new GroupDeclaration(
                segment,
                Objects.requireNonNull(display, "Operation 分组显示不能为空"),
                sortOrder,
                Arrays.asList(children));
    }

    /**
     * 声明父分组下的一个叶子，叶子编码是父分组编码加上动词，{@link Display} 按完整编码推导。
     *
     * @param verb       叶子编码的最后一段，例如 {@link Action#READ}；动词集合是开放的，业务可以自定义
     * @param sortOrder  叶子在同一父分组的子节点中的排序值
     * @param attributes 叶子携带的属性；不携带时省略。属性是否落在已登记的轴下，由 {@link OperationCatalog} 构造时逐个叶子检查
     * @return 可以继续调用 {@link LeafDeclaration#requires(OperationCode...)} 声明依赖，也可以直接传给
     *         {@link #root(TreeCode, int, Declaration...)} 或分组的声明
     * @throws IllegalArgumentException 同一属性重复声明时抛出
     * @throws NullPointerException     动词或任一属性为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _, _ -> new")
    public static @NotNull LeafDeclaration leaf(
            @NotNull Action verb,
            int sortOrder,
            @NotNull Attribute @NotNull... attributes) {
        return new LeafDeclaration(
                Objects.requireNonNull(verb, "Operation 叶子动词不能为空").code(),
                null,
                sortOrder,
                attributes);
    }

    /**
     * 声明父分组下的一个叶子，并指定与编码推导结果不同的 {@link Display}。
     *
     * @param verb       叶子编码的最后一段，例如 {@link Action#READ}
     * @param display    叶子显示
     * @param sortOrder  叶子在同一父分组的子节点中的排序值
     * @param attributes 叶子携带的属性；不携带时省略
     * @return 可以继续调用 {@link LeafDeclaration#requires(OperationCode...)} 声明依赖
     * @throws IllegalArgumentException 同一属性重复声明时抛出
     * @throws NullPointerException     动词、显示或任一属性为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _, _, _ -> new")
    public static @NotNull LeafDeclaration leaf(
            @NotNull Action verb,
            @NotNull Display display,
            int sortOrder,
            @NotNull Attribute @NotNull... attributes) {
        return new LeafDeclaration(
                Objects.requireNonNull(verb, "Operation 叶子动词不能为空").code(),
                Objects.requireNonNull(display, "Operation 叶子显示不能为空"),
                sortOrder,
                attributes);
    }

    /**
     * 声明父分组下的通配叶子，叶子编码是父分组编码加上最后一段 {@code *}，{@link Display} 按完整编码推导。
     *
     * <p>通配叶子表示父分组范围内的全部访问入口，例如 {@code CATALOG:ACCESS:*}。通配符只能由本方法声明，
     * {@link Action} 本身不接受 {@code *}。
     *
     * @param sortOrder  叶子在同一父分组的子节点中的排序值
     * @param attributes 叶子携带的属性；不携带时省略
     * @return 可以继续调用 {@link LeafDeclaration#requires(OperationCode...)} 声明依赖，也可以直接传给
     *         {@link #root(TreeCode, int, Declaration...)} 或分组的声明
     * @throws IllegalArgumentException 同一属性重复声明时抛出
     * @throws NullPointerException     任一属性为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _ -> new")
    public static @NotNull LeafDeclaration wildcardLeaf(
            int sortOrder,
            @NotNull Attribute @NotNull... attributes) {
        return new LeafDeclaration(TreeCode.ofSegment("*"), null, sortOrder, attributes);
    }

    /**
     * 声明父分组下的通配叶子，并指定与编码推导结果不同的 {@link Display}。
     *
     * @param display    叶子显示
     * @param sortOrder  叶子在同一父分组的子节点中的排序值
     * @param attributes 叶子携带的属性；不携带时省略
     * @return 可以继续调用 {@link LeafDeclaration#requires(OperationCode...)} 声明依赖
     * @throws IllegalArgumentException 同一属性重复声明时抛出
     * @throws NullPointerException     显示或任一属性为 {@code null} 时抛出
     */
    @CheckReturnValue
    @Contract("_, _, _ -> new")
    public static @NotNull LeafDeclaration wildcardLeaf(
            @NotNull Display display,
            int sortOrder,
            @NotNull Attribute @NotNull... attributes) {
        return new LeafDeclaration(
                TreeCode.ofSegment("*"),
                Objects.requireNonNull(display, "Operation 叶子显示不能为空"),
                sortOrder,
                attributes);
    }

    /**
     * 把一组子声明依次展开成挂在 {@code parentCode} 下的完整节点。
     *
     * @throws IllegalArgumentException 两个子节点的完整编码文本相同时抛出，分组与叶子同名也算重复
     * @throws NullPointerException     任一子声明为 {@code null} 时抛出
     */
    private static @NotNull List<@NotNull OperationNode> build(
            @NotNull TreeCode parentCode,
            @NotNull List<@NotNull Declaration> declarations) {
        List<OperationNode> nodes = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (Declaration declaration : declarations) {
            OperationNode node = Objects.requireNonNull(declaration, "Operation 声明不能为空").build(parentCode);
            if (!codes.add(node.code().format(NameFormat.COLON))) {
                throw new IllegalArgumentException("Operation 节点编码重复: " + node.code());
            }
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * @return 节点的完整编码；分组是 {@link TreeCode}，叶子覆盖为 {@link OperationCode}
     */
    public @NotNull TreeCode code() {
        return code;
    }

    /**
     * @return 父分组的完整编码；根分组返回空
     */
    public final @Nullable TreeCode parentCode() {
        return parentCode;
    }

    /**
     * @return 节点的名称消息键和说明消息键
     */
    public final @NotNull Display display() {
        return display;
    }

    /**
     * @return 节点在同一父分组的子节点中的排序值，根分组之间也按它排序
     */
    public final int sortOrder() {
        return sortOrder;
    }

    /**
     * @return 节点类型
     */
    public abstract @NotNull OperationNodeType type();

    /**
     * @return 类型正确且不可变的直接子节点；叶子为空
     */
    public abstract @NotNull @Unmodifiable List<? extends @NotNull OperationNode> children();

    /**
     * @return 子树中每一层都按排序值、再按编码排序的副本
     */
    @CheckReturnValue
    abstract @NotNull OperationNode sorted();

    /**
     * @param operations 保留的 Operation
     * @return 只保留指定 Operation 所在叶子的子树副本；子树里没有匹配的叶子时为空
     */
    @CheckReturnValue
    abstract @NotNull Optional<? extends @NotNull OperationNode> retaining(@NotNull Set<@NotNull Operation> operations);

    /**
     * 校验分组锚点或分组局部段。
     *
     * @param value           待校验的编码段，必须只包含一个节点
     * @param name            失败消息中使用的段名称
     * @param wildcardAllowed 为 {@code false} 时拒绝通配段 {@code *}
     * @return 通过校验的原编码段
     * @throws IllegalArgumentException 编码段包含多个节点，或在不允许通配时使用了 {@code *} 时抛出
     */
    private static @NotNull TreeCode requireSegment(
            @NotNull TreeCode value,
            @NotNull String name,
            boolean wildcardAllowed) {
        TreeCode checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.size() != 1 || (!wildcardAllowed && checked.equals(TreeCode.ofSegment("*")))) {
            throw new IllegalArgumentException(name + "必须是不含分隔符的单节点 TreeCode: " + checked);
        }
        return checked;
    }

    /**
     * 局部节点声明，由 {@link #group(TreeCode, int, Declaration...)}、
     * {@link #leaf(Action, int, Attribute...)} 和 {@link #wildcardLeaf(int, Attribute...)} 创建（另有带 {@link Display} 的重载）。
     *
     * <p>声明只保存相对父分组的局部段，不参与最终目录；{@link #root(TreeCode, int, Declaration...)} 把它展开成完整节点。
     * 构造方法私有，只有本类内的分组声明和叶子声明可以继承。
     */
    public abstract static class Declaration {

        private Declaration() {
        }

        /**
         * 把声明展开为挂在父分组下的完整节点。
         *
         * @param parentCode 父分组的完整编码
         */
        abstract @NotNull OperationNode build(@NotNull TreeCode parentCode);
    }

    /**
     * 分组声明，展开时递归展开全部子声明。
     */
    private static class GroupDeclaration extends Declaration {

        private final @Nullable Display display;

        private final @NotNull List<@NotNull Declaration> children;

        private final @NotNull TreeCode segment;

        private final int sortOrder;

        private GroupDeclaration(
                @NotNull TreeCode segment,
                @Nullable Display display,
                int sortOrder,
                @NotNull List<@NotNull Declaration> children) {
            this.segment = requireSegment(segment, "Operation 分组局部编码", false);
            this.display = display;
            this.sortOrder = sortOrder;
            this.children = List.copyOf(children);
        }

        @Override
        @NotNull
        Group build(@NotNull TreeCode parentCode) {
            TreeCode groupCode = parentCode.child(segment);
            return new Group(
                    groupCode,
                    parentCode,
                    display == null ? Display.of(DISPLAY_PREFIX, groupCode) : display,
                    sortOrder,
                    OperationNode.build(groupCode, children));
        }
    }

    /**
     * 叶子声明，展开时把动词追加到父分组编码后。
     *
     * <p>声明本身不可变；{@link #requires(OperationCode...)} 返回带依赖的新声明，不修改当前声明。
     */
    public static class LeafDeclaration extends Declaration {

        private final @Nullable Display display;

        private final @NotNull @Unmodifiable List<@NotNull OperationCode> requires;

        private final @NotNull @Unmodifiable Set<@NotNull Attribute> attributes;

        private final @NotNull TreeCode verb;

        private final int sortOrder;

        private LeafDeclaration(
                @NotNull TreeCode verb,
                @Nullable Display display,
                int sortOrder,
                @NotNull Attribute @NotNull [] attributes) {
            this.verb = requireSegment(verb, "Operation 叶子动词", true);
            this.display = display;
            this.sortOrder = sortOrder;
            LinkedHashSet<Attribute> checked = new LinkedHashSet<>();
            for (Attribute attribute : attributes) {
                Attribute value = Objects.requireNonNull(attribute, "Operation 叶子属性不能为空");
                if (!checked.add(value)) {
                    throw new IllegalArgumentException("Operation 叶子属性重复: " + value + ", verb=" + this.verb);
                }
            }
            this.attributes = Set.copyOf(checked);
            this.requires = List.of();
        }

        private LeafDeclaration(@NotNull LeafDeclaration source, @NotNull List<@NotNull OperationCode> requires) {
            this.verb = source.verb;
            this.display = source.display;
            this.sortOrder = source.sortOrder;
            this.attributes = source.attributes;
            this.requires = List.copyOf(requires);
        }

        /**
         * 声明本叶子依赖的其他叶子：持有本叶子的 Principal 还必须持有这些叶子，调用方才能完整使用本叶子。
         *
         * <p>这里只记录声明；目标叶子是否存在、是否自依赖、是否成环，由 {@link OperationCatalog} 构造时检查。
         * 依赖只按叶子逐个声明，不从动词、分组或属性推导。目录指纹 {@link OperationCatalog#fingerprint()} 不包含依赖。
         *
         * @param codes 被依赖叶子的完整编码；重复编码只保留一份，保持首次出现的顺序
         * @return 带依赖的新声明；当前声明不变，原有依赖会被替换
         * @throws NullPointerException 任一编码为 {@code null} 时抛出
         */
        @CheckReturnValue
        @Contract("_ -> new")
        public @NotNull LeafDeclaration requires(@NotNull OperationCode @NotNull... codes) {
            LinkedHashSet<OperationCode> checked = new LinkedHashSet<>();
            for (OperationCode code : codes) {
                checked.add(Objects.requireNonNull(code, "Operation 叶子依赖编码不能为空"));
            }
            return new LeafDeclaration(this, List.copyOf(checked));
        }

        @Override
        @NotNull
        Leaf build(@NotNull TreeCode parentCode) {
            OperationCode code = new OperationCode(parentCode.child(verb));
            return new Leaf(
                    code,
                    parentCode,
                    display == null ? Display.of(DISPLAY_PREFIX, code) : display,
                    sortOrder,
                    attributes,
                    requires);
        }
    }

    /**
     * 表示目录中的一个分组，直接子节点可以是子分组、叶子或两者混合，深度不限。
     */
    public static class Group extends OperationNode {

        private final @NotNull @Unmodifiable List<@NotNull OperationNode> children;

        private Group(
                @NotNull TreeCode code,
                @Nullable TreeCode parentCode,
                @NotNull Display display,
                int sortOrder,
                @NotNull List<@NotNull OperationNode> children) {
            super(code, parentCode, display, sortOrder);
            this.children = List.copyOf(children);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public @NotNull OperationNodeType type() {
            return OperationNodeType.GROUP;
        }

        /**
         * {@inheritDoc}
         *
         * <p>经过 {@link OperationCatalog} 登记的分组，子节点按排序值、再按编码排列。
         */
        @Override
        public @NotNull @Unmodifiable List<@NotNull OperationNode> children() {
            return children;
        }

        /**
         * 按深度优先、同级按 {@link #children()} 顺序展开全部后代叶子。
         *
         * @return 本分组下任意深度的叶子；没有叶子时为空
         */
        @CheckReturnValue
        public @NotNull @Unmodifiable List<@NotNull Leaf> descendantLeaves() {
            List<Leaf> leaves = new ArrayList<>();
            for (OperationNode child : children) {
                if (child instanceof Leaf leaf) {
                    leaves.add(leaf);
                } else {
                    leaves.addAll(((Group) child).descendantLeaves());
                }
            }
            return List.copyOf(leaves);
        }

        /**
         * @return 每一层都按节点排序规则复制的分组
         */
        @Override
        @CheckReturnValue
        @NotNull
        Group sorted() {
            return new Group(
                    code(),
                    parentCode(),
                    display(),
                    sortOrder(),
                    children.stream().map(OperationNode::sorted).sorted(NODE_ORDER).toList());
        }

        /**
         * @return 只保留指定 Operation 的分组；没有匹配叶子的子分组整体移除，本分组下没有匹配叶子时为空
         */
        @Override
        @CheckReturnValue
        @NotNull
        Optional<@NotNull Group> retaining(@NotNull Set<@NotNull Operation> operations) {
            List<OperationNode> retained = new ArrayList<>();
            for (OperationNode child : children) {
                child.retaining(operations).ifPresent(retained::add);
            }
            return retained.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Group(code(), parentCode(), display(), sortOrder(), retained));
        }
    }

    /**
     * 表示目录中可以直接授予 Principal 的固定 Operation，授权动作固定为 {@link Action#INVOKE}。
     */
    public static class Leaf extends OperationNode {

        private final @NotNull @Unmodifiable List<@NotNull OperationCode> requires;

        private final @NotNull Operation operation;

        private final @NotNull @Unmodifiable Set<@NotNull Attribute> attributes;

        private Leaf(
                @NotNull OperationCode code,
                @NotNull TreeCode parentCode,
                @NotNull Display display,
                int sortOrder,
                @NotNull Set<@NotNull Attribute> attributes,
                @NotNull List<@NotNull OperationCode> requires) {
            super(code, parentCode, display, sortOrder);
            this.operation = new Operation(code);
            this.attributes = Set.copyOf(attributes);
            this.requires = List.copyOf(requires);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public @NotNull OperationNodeType type() {
            return OperationNodeType.LEAF;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public @NotNull @Unmodifiable List<@NotNull OperationNode> children() {
            return List.of();
        }

        /**
         * @return 可以装入 Session 或由权限策略判断的 Operation 的完整编码，最后一段是动词
         */
        @Override
        public @NotNull OperationCode code() {
            return operation.code();
        }

        /**
         * @return 可以装入 Session 或由权限策略判断的 Operation
         */
        public @NotNull Operation operation() {
            return operation;
        }

        /**
         * @return 声明时携带的全部属性；未携带属性时为空，没有固定顺序
         */
        public @NotNull @Unmodifiable Set<@NotNull Attribute> attributes() {
            return attributes;
        }

        /**
         * @return 声明时写明本叶子依赖的其他叶子编码，保持声明顺序；没有声明时为空。
         *         经过 {@link OperationCatalog} 登记的叶子，这些编码一定存在于同一目录且不成环
         */
        public @NotNull @Unmodifiable List<@NotNull OperationCode> requires() {
            return requires;
        }

        /**
         * @param attribute 待判断的属性
         * @return 声明时携带的属性中包含与它完全相同的值时为 {@code true}
         */
        @Contract(pure = true)
        public boolean has(@NotNull Attribute attribute) {
            return attributes.contains(Objects.requireNonNull(attribute, "属性不能为空"));
        }

        /**
         * @param attribute 待判断的属性
         * @return 声明时携带的属性中包含该属性自身或它的任一后代时为 {@code true}
         */
        @Contract(pure = true)
        public boolean hasUnder(@NotNull Attribute attribute) {
            Attribute root = Objects.requireNonNull(attribute, "属性不能为空");
            return attributes.stream().anyMatch(value -> value.isUnder(root));
        }

        /**
         * @param axis 属性轴
         * @return 声明时携带的属性中属于该轴的全部值；没有时为空
         */
        @CheckReturnValue
        public @NotNull @Unmodifiable Set<@NotNull Attribute> get(@NotNull AttributeAxis axis) {
            AttributeAxis checked = Objects.requireNonNull(axis, "属性轴不能为空");
            return attributes.stream().filter(checked::contains).collect(Collectors.toUnmodifiableSet());
        }

        /**
         * @param axis 属性轴
         * @return 该轴上唯一的属性值；没有时为空
         * @throws IllegalStateException 该轴上携带多个属性值时抛出
         */
        @CheckReturnValue
        public @NotNull Optional<@NotNull Attribute> only(@NotNull AttributeAxis axis) {
            Set<Attribute> values = get(axis);
            if (values.size() > 1) {
                throw new IllegalStateException("Operation 叶子在属性轴上有多个值: " + code() + ", axis=" + axis.root());
            }
            return values.stream().findFirst();
        }

        /**
         * @return 叶子自身；叶子没有子节点，不需要排序
         */
        @Override
        @CheckReturnValue
        @NotNull
        Leaf sorted() {
            return this;
        }

        /**
         * @return 指定 Operation 包含本叶子时为本叶子，否则为空
         */
        @Override
        @CheckReturnValue
        @NotNull
        Optional<@NotNull Leaf> retaining(@NotNull Set<@NotNull Operation> operations) {
            return operations.contains(operation) ? Optional.of(this) : Optional.empty();
        }
    }
}
