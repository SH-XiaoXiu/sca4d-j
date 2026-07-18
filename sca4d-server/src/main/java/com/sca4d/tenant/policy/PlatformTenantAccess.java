package com.sca4d.tenant.policy;

import com.sca4d.common.access.AccessAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 管理端读取的 PLATFORM Principal 完整租户 ACL 快照。 */
public class PlatformTenantAccess {

    private final Long principalId;
    private final Long version;
    private final AccessAction defaultAction;
    private final List<TenantAccessRule> rules;

    public PlatformTenantAccess(
            @NotNull Long principalId,
            @NotNull Long version,
            @Nullable AccessAction defaultAction,
            @NotNull List<TenantAccessRule> rules) {
        this.principalId = principalId;
        this.version = version;
        this.defaultAction = defaultAction;
        this.rules = List.copyOf(rules);
    }

    public @NotNull Long getPrincipalId() {
        return principalId;
    }

    public @NotNull Long getVersion() {
        return version;
    }

    public @Nullable AccessAction getDefaultAction() {
        return defaultAction;
    }

    public @NotNull List<TenantAccessRule> getRules() {
        return rules;
    }
}
