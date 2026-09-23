package com.sca4d.core.api.permission;

import cn.hutool.crypto.digest.DigestUtil;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 收集业务模块的 Operation 聚合，并提供完整目录、ACTION 索引和子集投影。 */
public final class OperationCatalog {

    private static final @NotNull Comparator<OperationNode.Module> MODULE_ORDER = Comparator
            .comparingInt(OperationNode.Module::sortOrder)
            .thenComparing(OperationNode.Module::code);

    private final @NotNull @Unmodifiable List<OperationNode.Module> tree;

    private final @NotNull @Unmodifiable List<OperationNode.OperationAction> actions;

    private final @NotNull @Unmodifiable Map<@NotNull String, OperationNode.OperationAction> actionsByCode;

    private final @NotNull String fingerprint;

    /**
     * 收集并校验应用安装的 Operation 模块。
     *
     * @param modules 业务模块 Operation 聚合
     * @throws IllegalArgumentException 模块锚点或任意完整节点编码重复时抛出
     */
    public OperationCatalog(@NotNull Collection<? extends @NotNull OperationModule> modules) {
        List<OperationNode.Module> roots = new ArrayList<>();
        LinkedHashMap<String, OperationNode> nodesByCode = new LinkedHashMap<>();
        for (OperationModule module : Objects.requireNonNull(modules, "Operation 模块集合不能为空")) {
            OperationModule checked = Objects.requireNonNull(module, "Operation 模块不能为空");
            OperationNode.Module root = Objects.requireNonNull(
                    checked.operationTree(),
                    "Operation 模块不能返回空目录: " + checked.getClass().getName())
                    .sorted();
            register(root, nodesByCode);
            roots.add(root);
        }
        this.tree = List.copyOf(roots.stream().sorted(MODULE_ORDER).toList());

        LinkedHashMap<String, OperationNode.OperationAction> indexedActions = new LinkedHashMap<>();
        for (OperationNode.Module module : tree) {
            for (OperationNode.Resource resource : module.children()) {
                for (OperationNode.OperationAction action : resource.children()) {
                    indexedActions.put(action.code(), action);
                }
            }
        }
        this.actionsByCode = Map.copyOf(indexedActions);
        this.actions = List.copyOf(indexedActions.values());
        this.fingerprint = fingerprint(indexedActions.keySet());
    }

    /** 递归登记完整节点编码并拒绝跨模块冲突。 */
    private static void register(
            @NotNull OperationNode node,
            @NotNull Map<@NotNull String, @NotNull OperationNode> nodesByCode) {
        if (nodesByCode.putIfAbsent(node.code(), node) != null) {
            throw new IllegalArgumentException("Operation 节点编码重复: " + node.code());
        }
        node.children().forEach(child -> register(child, nodesByCode));
    }

    /** @return 按模块和节点排序的完整不可变 Operation 树 */
    public @NotNull @Unmodifiable List<OperationNode.Module> tree() {
        return tree;
    }

    /**
     * 返回只包含指定 Operation 的目录投影。
     *
     * @param operations 必须全部来自当前 Catalog 的具体 Operation
     * @return 自动移除空 RESOURCE 和 MODULE 后的不可变树
     * @throws IllegalArgumentException 包含当前 Catalog 未声明的 Operation 时抛出
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<OperationNode.Module> tree(
            @NotNull Collection<@NotNull Operation> operations) {
        Set<Operation> checked = canonicalOperations(operations);
        return tree.stream().map(module -> module.retaining(checked)).flatMap(Optional::stream).toList();
    }

    /** @return 按目录顺序排列的全部不可变 ACTION */
    public @NotNull @Unmodifiable List<OperationNode.OperationAction> actions() {
        return actions;
    }

    /**
     * 返回具体 Operation 编码集合的稳定指纹。
     *
     * <p>显示名称、翻译键、排序值和模块登记顺序不参与计算；新增、删除或修改具体 Operation 编码会改变结果。
     *
     * @return SHA-256 摘要的 Base64URL 无填充文本
     */
    public @NotNull String fingerprint() {
        return fingerprint;
    }

    /**
     * 返回指定 Operation 对应的 ACTION，并保持 Catalog 目录顺序。
     *
     * @param operations 必须全部来自当前 Catalog 的具体 Operation
     * @return 不可变 ACTION 列表
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<OperationNode.OperationAction> actions(
            @NotNull Collection<@NotNull Operation> operations) {
        Set<Operation> checked = canonicalOperations(operations);
        return actions.stream().filter(action -> checked.contains(action.operation())).toList();
    }

    /** @return 匹配的 ACTION；编码不存在时为空 */
    @CheckReturnValue
    public @NotNull Optional<OperationNode.OperationAction> action(@NotNull String code) {
        String normalized = new Operation(TextUtils.requireText(code, "Operation 编码")).value();
        return Optional.ofNullable(actionsByCode.get(normalized));
    }

    /** @return 必须存在且可以直接授权的 ACTION */
    @CheckReturnValue
    public @NotNull OperationNode.OperationAction actionByCode(@NotNull String code) {
        String checkedCode = new Operation(TextUtils.requireText(code, "Operation 编码")).value();
        return action(checkedCode)
                .orElseThrow(() -> new IllegalArgumentException("未知或不可授权的 Operation: " + checkedCode));
    }

    /** 校验并去重一组来自当前 Catalog 的 Operation。 */
    private @NotNull Set<@NotNull Operation> canonicalOperations(
            @NotNull Collection<@NotNull Operation> operations) {
        Collection<Operation> source = Objects.requireNonNull(operations, "Operation 集合不能为空");
        LinkedHashSet<Operation> checked = new LinkedHashSet<>();
        for (Operation operation : source) {
            Operation value = Objects.requireNonNull(operation, "Operation 不能为空");
            checked.add(actionByCode(value.value()).operation());
        }
        return Set.copyOf(checked);
    }

    /** 只摘要排序后的具体 Operation 编码，避免展示元数据变化使在线 Session 失效。 */
    private static @NotNull String fingerprint(@NotNull Collection<@NotNull String> actionCodes) {
        String canonical = Objects.requireNonNull(actionCodes, "Operation 编码集合不能为空")
                .stream()
                .sorted()
                .collect(Collectors.joining("\n"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(DigestUtil.sha256(canonical));
    }
}
