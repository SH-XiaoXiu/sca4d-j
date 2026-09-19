package com.sca4d.iam.rbac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按 {@link RbacDomain} 查找负责标记对应业务 Account Session 的实现。 */
public final class RbacSessionInvalidationRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RbacSessionInvalidationRegistry.class);

    private final @NotNull @Unmodifiable Map<@NotNull RbacDomain, @NotNull RbacSessionInvalidation> invalidations;

    /**
     * 检查每个 RBAC 领域至多对应一个 Session 失效实现。
     *
     * @param invalidations 应用已经安装的 Session 失效实现
     * @throws IllegalStateException 多个实现使用相同 RBAC 领域时抛出
     */
    public RbacSessionInvalidationRegistry(
            @NotNull List<? extends @NotNull RbacSessionInvalidation> invalidations) {
        LinkedHashMap<RbacDomain, RbacSessionInvalidation> indexed = new LinkedHashMap<>();
        for (RbacSessionInvalidation invalidation : Objects.requireNonNull(invalidations, "Session 失效实现不能为空")) {
            RbacSessionInvalidation checked = Objects.requireNonNull(invalidation, "Session 失效实现不能为空");
            RbacDomain domain = Objects.requireNonNull(checked.domain(), "Session 失效实现的 RBAC 领域不能为空");
            if (indexed.putIfAbsent(domain, checked) != null) {
                throw new IllegalStateException("多个 Session 失效实现使用了相同 RBAC 领域: " + domain);
            }
        }
        this.invalidations = Map.copyOf(indexed);
        LOG.debug("RBAC Session 失效实现注册完成 | invalidationCount={}", this.invalidations.size());
    }

    /**
     * 当前领域安装了 Session 失效实现时立即标记关联 Account Session。
     *
     * @param scope 发生权限变化的 RBAC 范围
     * @param principalIds 受影响的 Principal
     */
    public void invalidate(
            @NotNull RbacScope scope,
            @NotNull List<@NotNull Long> principalIds) {
        RbacScope checked = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        RbacSessionInvalidation invalidation = invalidations.get(checked.domain());
        if (invalidation == null) {
            LOG.trace("跳过 RBAC Session 失效 | scope={} reason=no-invalidation", checked);
            return;
        }
        LOG.trace(
                "RBAC Session 失效实现路由完成 | scope={} invalidationType={}",
                checked,
                invalidation.getClass().getName());
        invalidation.invalidate(checked, List.copyOf(principalIds));
    }
}
