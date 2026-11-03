package com.sca4d.core.api.permission;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Attribute;
import com.sca4d.core.api.name.code.AttributeAxis;
import com.sca4d.core.api.name.code.AttributeConstraint;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收集业务模块的 Operation 目录，并提供完整目录、叶子索引和子集投影。
 */
public class OperationCatalog {

    private static final @NotNull Comparator<OperationNode.Group> ROOT_ORDER = Comparator
            .comparingInt(OperationNode.Group::sortOrder)
            .thenComparing(OperationNode.Group::code);

    private final @NotNull @Unmodifiable List<OperationNode.Group> tree;

    private final @NotNull @Unmodifiable List<OperationNode.Leaf> leaves;

    private final @NotNull @Unmodifiable Map<@NotNull OperationCode, OperationNode.Leaf> leavesByCode;

    private final @NotNull String fingerprint;

    private final @NotNull @Unmodifiable List<AttributeAxis> axes;

    /**
     * 收集并校验应用安装的 Operation 模块和属性轴。
     *
     * @param modules 业务模块 Operation 目录
     * @param axes    应用登记的属性轴；没有属性时传空集合
     * @throws IllegalArgumentException 任意完整节点编码重复（包括不同模块的根分组锚点相同）、轴根重复或嵌套、
     *                                  叶子属性不在任何登记轴下、或叶子违反 {@link AttributeConstraint#REQUIRED_EXCLUSIVE}
     *                                  轴的取值约束、叶子依赖不存在的叶子、依赖自身或依赖成环时抛出
     * @throws NullPointerException     模块、轴或其集合为 {@code null} 时抛出
     */
    public OperationCatalog(
            @NotNull Collection<? extends @NotNull OperationModule> modules,
            @NotNull Collection<? extends @NotNull AttributeAxis> axes) {
        List<OperationNode.Group> roots = new ArrayList<>();
        Set<String> registeredCodes = new HashSet<>();
        for (OperationModule module : Objects.requireNonNull(modules, "Operation 模块集合不能为空")) {
            OperationModule checked = Objects.requireNonNull(module, "Operation 模块不能为空");
            OperationNode.Group root = Objects.requireNonNull(
                    checked.operationTree(),
                    "Operation 模块不能返回空目录: " + checked.getClass().getName())
                    .sorted();
            register(root, registeredCodes);
            roots.add(root);
        }
        this.tree = List.copyOf(roots.stream().sorted(ROOT_ORDER).toList());

        LinkedHashMap<OperationCode, OperationNode.Leaf> indexedLeaves = new LinkedHashMap<>();
        for (OperationNode.Group root : tree) {
            for (OperationNode.Leaf leaf : root.descendantLeaves()) {
                indexedLeaves.put(leaf.operation().code(), leaf);
            }
        }
        this.leavesByCode = Map.copyOf(indexedLeaves);
        this.leaves = List.copyOf(indexedLeaves.values());
        this.fingerprint = fingerprint(indexedLeaves.keySet());

        // 校验属性轴自身：轴根不重复，也不互相嵌套，保证每个属性最多属于一条轴。
        List<AttributeAxis> checkedAxes = new ArrayList<>();
        for (AttributeAxis axis : Objects.requireNonNull(axes, "属性轴集合不能为空")) {
            AttributeAxis checked = Objects.requireNonNull(axis, "属性轴不能为空");
            for (AttributeAxis registered : checkedAxes) {
                if (registered.root().equals(checked.root())) {
                    throw new IllegalArgumentException("属性轴根重复: " + checked.root());
                }
                if (registered.root().isUnder(checked.root()) || checked.root().isUnder(registered.root())) {
                    throw new IllegalArgumentException(
                            "属性轴根不能嵌套: " + registered.root() + " 与 " + checked.root());
                }
            }
            checkedAxes.add(checked);
        }
        this.axes = List.copyOf(checkedAxes);

        // 校验每个叶子的属性：必须落在登记轴下，REQUIRED_EXCLUSIVE 轴必须恰好有一个值。
        for (OperationNode.Leaf leaf : leaves) {
            for (Attribute attribute : leaf.attributes()) {
                if (this.axes.stream().noneMatch(axis -> axis.contains(attribute))) {
                    boolean isAxisRoot = this.axes.stream().anyMatch(axis -> axis.root().equals(attribute));
                    throw new IllegalArgumentException(
                            (isAxisRoot ? "属性不能直接使用轴根: " : "属性未登记轴: ")
                                    + attribute
                                    + ", leaf="
                                    + leaf.code());
                }
            }
            for (AttributeAxis axis : this.axes) {
                if (axis.constraint() != AttributeConstraint.REQUIRED_EXCLUSIVE) {
                    continue;
                }
                int count = leaf.get(axis).size();
                if (count == 0) {
                    throw new IllegalArgumentException(
                            "Operation 叶子缺少必填属性轴的值: leaf=" + leaf.code() + ", axis=" + axis.root());
                }
                if (count > 1) {
                    throw new IllegalArgumentException(
                            "Operation 叶子在互斥属性轴上有多个值: leaf="
                                    + leaf.code()
                                    + ", axis="
                                    + axis.root());
                }
            }
        }

        // 校验叶子之间的依赖：目标必须是本目录的叶子，不能依赖自身，也不能经由其他叶子绕回自己。
        for (OperationNode.Leaf leaf : leaves) {
            for (OperationCode required : leaf.requires()) {
                if (required.equals(leaf.code())) {
                    throw new IllegalArgumentException("Operation 叶子不能依赖自身: leaf=" + leaf.code());
                }
                if (!leavesByCode.containsKey(required)) {
                    throw new IllegalArgumentException(
                            "Operation 叶子依赖的叶子不存在: leaf=" + leaf.code() + ", requires=" + required);
                }
            }
        }
        Set<OperationCode> finished = new HashSet<>();
        for (OperationNode.Leaf leaf : leaves) {
            rejectCycle(leaf, new LinkedHashSet<>(), finished);
        }
    }

    /**
     * 深度优先沿依赖查找环。{@code path} 是从起点到当前叶子的依赖路径，{@code finished} 是已确认不在环上的叶子。
     *
     * @throws IllegalArgumentException 依赖回到路径上的叶子时抛出，消息列出整条环
     */
    private void rejectCycle(
            @NotNull OperationNode.Leaf leaf,
            @NotNull LinkedHashSet<OperationCode> path,
            @NotNull Set<OperationCode> finished) {
        if (finished.contains(leaf.code())) {
            return;
        }
        path.add(leaf.code());
        for (OperationCode required : leaf.requires()) {
            if (path.contains(required)) {
                String cycle = path.stream()
                        .dropWhile(code -> !code.equals(required))
                        .map(code -> code.format(NameFormat.COLON))
                        .collect(Collectors.joining(" -> "));
                throw new IllegalArgumentException(
                        "Operation 叶子依赖成环: leaf="
                                + leaf.code()
                                + ", requires="
                                + required
                                + ", cycle="
                                + cycle
                                + " -> "
                                + required.format(NameFormat.COLON));
            }
            rejectCycle(leavesByCode.get(required), path, finished);
        }
        path.remove(leaf.code());
        finished.add(leaf.code());
    }

    /**
     * 递归登记完整节点编码并拒绝跨模块冲突。分组与叶子按编码文本比较，因此不同模块里
     * 同名的分组和叶子也会被拒绝。
     */
    private static void register(@NotNull OperationNode node, @NotNull Set<@NotNull String> registeredCodes) {
        if (!registeredCodes.add(node.code().format(NameFormat.COLON))) {
            throw new IllegalArgumentException("Operation 节点编码重复: " + node.code());
        }
        node.children().forEach(child -> register(child, registeredCodes));
    }

    /**
     * @return 按排序值、再按编码排列根分组的完整不可变 Operation 树，每一层子节点同样按此规则排列
     */
    public @NotNull @Unmodifiable List<OperationNode.Group> tree() {
        return tree;
    }

    /**
     * 返回只包含指定 Operation 的目录投影。
     *
     * @param operations 必须全部来自当前 Catalog 的具体 Operation
     * @return 自动移除没有匹配叶子的分组（任意深度）后的不可变树，顺序与 {@link #tree()} 一致
     * @throws IllegalArgumentException 包含当前 Catalog 未声明的 Operation 时抛出
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<OperationNode.Group> tree(
            @NotNull Collection<@NotNull Operation> operations) {
        Set<Operation> checked = canonicalOperations(operations);
        return tree.stream().map(root -> root.retaining(checked)).flatMap(Optional::stream).toList();
    }

    /**
     * @return 按深度优先目录顺序排列的全部不可变叶子
     */
    public @NotNull @Unmodifiable List<OperationNode.Leaf> leaves() {
        return leaves;
    }

    /**
     * @return 应用登记的属性轴，保持传入顺序
     */
    public @NotNull @Unmodifiable List<AttributeAxis> axes() {
        return axes;
    }

    /**
     * @param attribute 属性值
     * @return 声明时携带与该值完全相同属性的叶子，保持 Catalog 目录顺序
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<OperationNode.Leaf> leavesWith(@NotNull Attribute attribute) {
        Attribute checked = Objects.requireNonNull(attribute, "属性不能为空");
        return leaves.stream().filter(leaf -> leaf.has(checked)).toList();
    }

    /**
     * @param attribute 属性值或属性根
     * @return 声明时携带该属性或其任一后代的叶子，保持 Catalog 目录顺序
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<OperationNode.Leaf> leavesUnder(@NotNull Attribute attribute) {
        Attribute checked = Objects.requireNonNull(attribute, "属性不能为空");
        return leaves.stream().filter(leaf -> leaf.hasUnder(checked)).toList();
    }

    /**
     * 具体 Operation 编码集合的稳定指纹。
     *
     * <p>显示名称、翻译键、排序值、属性、叶子依赖、分组层级和模块登记顺序不参与计算；新增、删除或修改具体 Operation 编码会改变结果。
     *
     * @return SHA-256 摘要的 Base64URL 无填充文本
     */
    public @NotNull String fingerprint() {
        return fingerprint;
    }

    /**
     * 返回指定 Operation 对应的叶子，并保持 Catalog 目录顺序。
     *
     * @param operations 必须全部来自当前 Catalog 的具体 Operation
     * @return 不可变叶子列表
     * @throws IllegalArgumentException 包含当前 Catalog 未声明的 Operation 时抛出
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<OperationNode.Leaf> leaves(
            @NotNull Collection<@NotNull Operation> operations) {
        Set<Operation> checked = canonicalOperations(operations);
        return leaves.stream().filter(leaf -> checked.contains(leaf.operation())).toList();
    }

    /**
     * @return 匹配的叶子；编码不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<OperationNode.Leaf> leaf(@NotNull String code) {
        return leaf(new OperationCode(TextUtils.requireText(code, "Operation 编码")));
    }

    /**
     * @return 匹配的叶子；编码不存在时为空
     */
    @CheckReturnValue
    public @NotNull Optional<OperationNode.Leaf> leaf(@NotNull OperationCode code) {
        OperationCode checked = Objects.requireNonNull(code, "Operation 编码不能为空");
        return Optional.ofNullable(leavesByCode.get(checked));
    }

    /**
     * @return 必须存在且可以直接授权的叶子
     * @throws IllegalArgumentException 编码不是合法 Operation 编码，或当前 Catalog 没有该叶子时抛出
     */
    @CheckReturnValue
    public @NotNull OperationNode.Leaf leafByCode(@NotNull String code) {
        return leafByCode(new OperationCode(TextUtils.requireText(code, "Operation 编码")));
    }

    /**
     * @return 必须存在且可以直接授权的叶子
     * @throws IllegalArgumentException 当前 Catalog 没有该叶子时抛出
     */
    @CheckReturnValue
    public @NotNull OperationNode.Leaf leafByCode(@NotNull OperationCode code) {
        OperationCode checkedCode = Objects.requireNonNull(code, "Operation 编码不能为空");
        return leaf(checkedCode)
                .orElseThrow(() -> new IllegalArgumentException("未知或不可授权的 Operation: " + checkedCode));
    }

    /**
     * 校验并去重一组来自当前 Catalog 的 Operation。
     */
    private @NotNull Set<@NotNull Operation> canonicalOperations(
            @NotNull Collection<@NotNull Operation> operations) {
        Collection<Operation> source = Objects.requireNonNull(operations, "Operation 集合不能为空");
        LinkedHashSet<Operation> checked = new LinkedHashSet<>();
        for (Operation operation : source) {
            Operation value = Objects.requireNonNull(operation, "Operation 不能为空");
            checked.add(leafByCode(value.code()).operation());
        }
        return Set.copyOf(checked);
    }

    /**
     * 只摘要排序后的具体 Operation 编码，避免展示元数据变化使在线 Session 失效。
     */
    private static @NotNull String fingerprint(@NotNull Collection<@NotNull OperationCode> leafCodes) {
        String canonical = Objects.requireNonNull(leafCodes, "Operation 编码集合不能为空")
                .stream()
                .sorted()
                .map(code -> code.format(NameFormat.COLON))
                .collect(Collectors.joining("\n"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(DigestUtil.sha256(canonical));
    }
}
