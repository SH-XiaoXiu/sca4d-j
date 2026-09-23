package com.sca4d.iam.rbac;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.permission.OperationNode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据各 Operation 模块的领域行为构建并查找 RBAC 权限选择规则。
 */
public final class PermissionSelectionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionSelectionRegistry.class);

    private final @NotNull @Unmodifiable Map<@NotNull RbacDomain, @NotNull PermissionSelections> selections;

    /**
     * 为每个模块声明的 RBAC 领域构建唯一权限选择规则。
     *
     * @param catalog 应用已经安装的完整 Operation Catalog
     * @param modules 同时声明 Operation 树和 RBAC 领域行为的模块
     * @throws IllegalStateException 模块缺失、锚点重复、领域为空或 ACTION 不能授予任何领域时抛出
     */
    public PermissionSelectionRegistry(
            @NotNull OperationCatalog catalog,
            @NotNull List<? extends @NotNull RbacOperationModule> modules) {
        OperationCatalog checkedCatalog = Objects.requireNonNull(catalog, "Operation Catalog 不能为空");
        LinkedHashMap<TreeCode, RbacOperationModule> modulesByCode = new LinkedHashMap<>();
        LinkedHashMap<RbacDomain, List<Operation>> operationsByDomain = new LinkedHashMap<>();
        for (RbacOperationModule module : Objects.requireNonNull(modules, "RBAC Operation 模块不能为空")) {
            RbacOperationModule checked = Objects.requireNonNull(module, "RBAC Operation 模块不能为空");
            OperationNode.Module tree = Objects.requireNonNull(checked.operationTree(), "RBAC Operation 模块目录不能为空");
            if (modulesByCode.putIfAbsent(tree.code(), checked) != null) {
                throw new IllegalStateException("多个 RBAC Operation 模块使用相同锚点: " + tree.code());
            }
            Set<RbacDomain> domains = Set.copyOf(Objects.requireNonNull(checked.domains(), "RBAC Operation 模块领域不能为空"));
            if (domains.isEmpty()) {
                throw new IllegalStateException("RBAC Operation 模块必须声明至少一个非空领域: " + tree.code());
            }
            List<Operation> moduleOperations = tree.children()
                    .stream()
                    .flatMap(resource -> resource.children().stream())
                    .map(OperationNode.OperationAction::operation)
                    .toList();
            for (Operation operation : moduleOperations) {
                boolean grantable = false;
                for (RbacDomain domain : domains) {
                    if (checked.canGrant(domain, operation)) {
                        operationsByDomain.computeIfAbsent(domain, ignored -> new ArrayList<>()).add(operation);
                        grantable = true;
                    }
                }
                if (!grantable) {
                    throw new IllegalStateException("Operation 不能授予模块声明的任何 RBAC 领域: " + operation.code());
                }
            }
        }

        LinkedHashSet<TreeCode> catalogModules = checkedCatalog.tree()
                .stream()
                .map(OperationNode.Module::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!catalogModules.equals(new LinkedHashSet<>(modulesByCode.keySet()))) {
            LinkedHashSet<TreeCode> missing = new LinkedHashSet<>(catalogModules);
            missing.removeAll(modulesByCode.keySet());
            LinkedHashSet<TreeCode> unexpected = new LinkedHashSet<>(modulesByCode.keySet());
            unexpected.removeAll(catalogModules);
            throw new IllegalStateException(
                    "Operation Catalog 与 RBAC Operation 模块不一致 | missing=" + missing + " unexpected=" + unexpected);
        }

        LinkedHashMap<RbacDomain, PermissionSelections> indexed = new LinkedHashMap<>();
        operationsByDomain.forEach(
                (domain, operations) -> indexed.put(
                        domain,
                        new PermissionSelections(domain, operations)));
        this.selections = Map.copyOf(indexed);
        LOG.debug("RBAC 权限选择规则注册完成 | domainCount={}", this.selections.size());
    }

    /**
     * 取得指定领域的权限选择规则。
     *
     * @param domain RBAC 领域
     * @return 唯一权限选择规则
     * @throws IllegalArgumentException 应用没有安装对应规则时抛出
     */
    @CheckReturnValue
    public @NotNull PermissionSelections selection(@NotNull RbacDomain domain) {
        RbacDomain checked = Objects.requireNonNull(domain, "RBAC 领域不能为空");
        PermissionSelections selection = selections.get(checked);
        if (selection == null) {
            throw new IllegalArgumentException("应用没有安装对应的 RBAC 权限选择规则: " + checked);
        }
        return selection;
    }

    /**
     * @return 应用已经安装权限选择规则的不可变 RBAC 领域
     */
    public @NotNull @Unmodifiable Set<@NotNull RbacDomain> domains() {
        return selections.keySet();
    }
}
