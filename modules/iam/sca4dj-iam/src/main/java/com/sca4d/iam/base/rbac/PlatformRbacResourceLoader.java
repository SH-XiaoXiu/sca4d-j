package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.authentication.principalsession.ResourceLoader;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.iam.base.PlatformPrincipal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 为 Session 中的 PlatformPrincipal 读取启用角色提供的具体 Operation。 */
public final class PlatformRbacResourceLoader implements ResourceLoader {

    private final @NotNull PlatformRbacService rbac;

    /**
     * 创建 Platform Operation Loader。
     *
     * @param rbac Platform RBAC 服务
     */
    public PlatformRbacResourceLoader(@NotNull PlatformRbacService rbac) {
        this.rbac = Objects.requireNonNull(rbac, "Platform RBAC 服务不能为空");
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull Operation>> load(
            @NotNull Collection<? extends @NotNull Principal> principals) {
        LinkedHashSet<Long> principalIds = Objects.requireNonNull(principals, "Principal 集合不能为空")
                .stream()
                .filter(PlatformPrincipal.class::isInstance)
                .map(Principal::principalId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (principalIds.isEmpty()) {
            return Map.of();
        }
        return rbac.findGrantedOperations(principalIds);
    }
}
