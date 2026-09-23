package com.sca4d.iam.rbac;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationNode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 校验可由管理 API 保存的 ACTION 编码，并把内部全量选择符展开为具体 {@link Operation}。
 */
public final class PermissionSelections {

    /**
     * 数据库内部表示 Catalog 当前全部 ACTION 的选择符。
     */
    public static final @NotNull PermissionSelector ALL = PermissionSelector.ALL;

    private final @NotNull RbacDomain domain;

    private final @NotNull @Unmodifiable Set<@NotNull TreeCode> actionCodes;

    private final @NotNull @Unmodifiable List<@NotNull Operation> allOperations;

    /**
     * 使用应用已经完成校验的 Operation Catalog 创建选择规则。
     *
     * @param domain  RBAC 领域
     * @param catalog 应用 Operation Catalog
     */
    public PermissionSelections(
            @NotNull RbacDomain domain,
            @NotNull OperationCatalog catalog) {
        this(
                domain,
                Objects.requireNonNull(catalog, "Operation Catalog 不能为空")
                        .actions()
                        .stream()
                        .map(OperationNode.OperationAction::operation)
                        .toList());
    }

    /**
     * 使用一个 RBAC 领域允许分配的具体 Operation 创建选择规则。
     *
     * @param domain     RBAC 领域
     * @param operations 当前领域角色可以保存的具体 Operation
     */
    public PermissionSelections(
            @NotNull RbacDomain domain,
            @NotNull Collection<@NotNull Operation> operations) {
        this.domain = Objects.requireNonNull(domain, "RBAC 领域不能为空");
        Collection<Operation> checkedOperations = Objects.requireNonNull(operations, "Operation 集合不能为空");
        if (checkedOperations.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("Operation 不能为空");
        }
        this.allOperations = checkedOperations.stream()
                .sorted(Comparator.comparing(Operation::code))
                .toList();
        this.actionCodes = this.allOperations.stream()
                .map(Operation::code)
                .collect(Collectors.toUnmodifiableSet());
        if (this.actionCodes.size() != this.allOperations.size()) {
            throw new IllegalArgumentException("RBAC 权限目录包含重复 Operation");
        }
    }

    /**
     * @return 当前选择规则所属的 RBAC 领域
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull RbacDomain domain() {
        return domain;
    }

    /**
     * @return 当前 RBAC 领域角色可以获得的全部具体 Operation
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable List<@NotNull Operation> operations() {
        return Collections.unmodifiableList(new ArrayList<>(allOperations));
    }

    /**
     * 校验管理 API 提交的 ACTION 编码。
     *
     * @param operationCodes 待保存编码
     * @return 保持提交顺序的不可变编码列表
     * @throws BusinessException 编码重复、未声明或为内部全量选择符时抛出
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PermissionSelector> validateGrantable(
            @NotNull Collection<@NotNull String> operationCodes) {
        Collection<@NotNull String> checked = Objects.requireNonNull(operationCodes, "Operation 编码集合不能为空");
        List<TreeCode> codes = checked.stream().map(TreeCode::ofColon).toList();
        return validateGrantableCodes(codes);
    }

    /**
     * 校验已经在字符串边界解析完成的 Operation TreeCode。
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PermissionSelector> validateGrantableCodes(
            @NotNull Collection<@NotNull TreeCode> operationCodes) {
        LinkedHashSet<TreeCode> unique = new LinkedHashSet<>();
        for (TreeCode code : Objects.requireNonNull(operationCodes, "Operation 编码集合不能为空")) {
            TreeCode checkedCode = Objects.requireNonNull(code, "Operation 编码不能为空");
            if (!actionCodes.contains(checkedCode) || !unique.add(checkedCode)) {
                throw new BusinessException(RbacErrorCode.OPERATION_INVALID);
            }
        }
        return unique.stream().map(PermissionSelector::new).toList();
    }

    /**
     * 校验已经由代码层保存的 Operation，不把 TreeCode 转回字符串。
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PermissionSelector> validateGrantableOperations(
            @NotNull Collection<@NotNull Operation> operations) {
        LinkedHashSet<TreeCode> unique = new LinkedHashSet<>();
        for (Operation operation : Objects.requireNonNull(operations, "Operation 集合不能为空")) {
            TreeCode code = Objects.requireNonNull(operation, "Operation 不能为空").code();
            if (!actionCodes.contains(code) || !unique.add(code)) {
                throw new BusinessException(RbacErrorCode.OPERATION_INVALID);
            }
        }
        return unique.stream().map(PermissionSelector::new).toList();
    }

    /**
     * 把数据库保存的选择符展开为 Session 可以保存的具体 Operation。
     *
     * @param selectors 数据库选择符
     * @return 去重并按编码排序的具体 Operation；结果永远不包含 {@code Operation("*")}
     * @throws IllegalStateException 数据库包含 Catalog 未声明的具体 ACTION 时抛出
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull Operation> expand(
            @NotNull Collection<@NotNull PermissionSelector> selectors) {
        Collection<@NotNull PermissionSelector> checked = Objects.requireNonNull(selectors, "Operation 选择符集合不能为空");
        if (checked.contains(ALL)) {
            return List.copyOf(allOperations);
        }
        LinkedHashSet<TreeCode> unique = new LinkedHashSet<>();
        for (PermissionSelector selector : checked) {
            PermissionSelector checkedSelector = Objects.requireNonNull(selector, "Operation 选择符不能为空");
            TreeCode value = checkedSelector.code();
            if (!actionCodes.contains(value)) {
                throw new IllegalStateException("RBAC 数据包含 Operation Catalog 未声明的 ACTION: " + value);
            }
            unique.add(value);
        }
        return unique.stream().sorted().map(Operation::new).toList();
    }
}
