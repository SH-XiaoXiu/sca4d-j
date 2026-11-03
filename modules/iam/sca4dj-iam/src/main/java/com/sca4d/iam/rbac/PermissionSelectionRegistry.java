package com.sca4d.iam.rbac;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 根据 Operation 叶子的可授予领域属性构建并查找 RBAC 权限选择规则。
 */
public class PermissionSelectionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionSelectionRegistry.class);

    private final @NotNull @Unmodifiable Map<@NotNull RbacDomain, @NotNull PermissionSelections> selections;

    /**
     * 按叶子声明的 {@link RbacGrantable#GRANTABLE} 属性，为每个出现的 RBAC 领域构建唯一权限选择规则。
     *
     * @param catalog 应用已经安装的完整 Operation Catalog
     * @throws IllegalStateException 叶子没有声明任何可授予领域，或 {@code GRANTABLE} 属性形式不正确时抛出
     */
    public PermissionSelectionRegistry(@NotNull OperationCatalog catalog) {
        LinkedHashMap<RbacDomain, List<Operation>> operationsByDomain = new LinkedHashMap<>();
        for (OperationNode.Leaf leaf : Objects.requireNonNull(catalog, "Operation Catalog 不能为空").leaves()) {
            Set<RbacDomain> domains = RbacGrantable.domainsOf(leaf);
            if (domains.isEmpty()) {
                throw new IllegalStateException("Operation 没有声明任何可授予的 RBAC 领域: " + leaf.code());
            }
            for (RbacDomain domain : domains) {
                operationsByDomain.computeIfAbsent(domain, ignored -> new ArrayList<>()).add(leaf.operation());
            }
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
