package com.sca4d.iam.acl;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.iam.DomainScope;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 汇总 ACL 认领的资源类型并在启动时拒绝冲突。
 */
public class AclResourceTypes {
    private final @NotNull @Unmodifiable Map<@NotNull TreeCode, @NotNull DomainScope> scopes;

    /**
     * 汇总全部 ACL 实现认领的资源类型。
     *
     * @param repositories ACL Repository 注册表
     */
    public AclResourceTypes(@NotNull AclRepositoryRegistry repositories) {
        LinkedHashMap<TreeCode, DomainScope> values = new LinkedHashMap<>();
        for (AclRepository repository : repositories.repositories()) {
            for (TreeCode type : repository.resourceTypes()) {
                if (Operation.RESOURCE_TYPE.equals(type)) {
                    throw new IllegalStateException("ACL 资源类型非法或与 Operation 冲突: " + type);
                }
                // PermissionAssessment 要求每种资源恰好由一个策略处理，因此启动时拒绝跨领域重复认领。
                DomainScope previous = values.putIfAbsent(type, repository.scope());
                if (previous != null) {
                    throw new IllegalStateException("ACL 资源类型跨领域重复: " + type);
                }
            }
        }
        scopes = Map.copyOf(values);
    }

    /**
     * 判断资源类型是否由 ACL 策略处理。
     *
     * @param resourceType 资源类型
     * @return 已认领时为 {@code true}
     */
    @CheckReturnValue
    public boolean contains(@NotNull TreeCode resourceType) {
        return scopes.containsKey(Objects.requireNonNull(resourceType, "ACL 资源类型不能为空"));
    }

    /**
     * 返回资源类型唯一归属的领域范围。
     *
     * @param resourceType 资源类型
     * @return 领域范围
     */
    @CheckReturnValue
    public @NotNull DomainScope scope(@NotNull TreeCode resourceType) {
        TreeCode checked = Objects.requireNonNull(resourceType, "ACL 资源类型不能为空");
        DomainScope scope = scopes.get(checked);
        if (scope == null) {
            throw new IllegalArgumentException("ACL 资源类型未注册: " + checked);
        }
        return scope;
    }
}
