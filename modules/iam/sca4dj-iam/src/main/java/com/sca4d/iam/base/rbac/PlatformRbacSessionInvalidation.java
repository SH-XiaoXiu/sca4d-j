package com.sca4d.iam.base.rbac;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacScope;
import com.sca4d.iam.rbac.RbacSessionInvalidation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 在 Platform 角色数据发生变化时标记关联 PlatformAccount 的 Session。 */
public class PlatformRbacSessionInvalidation implements RbacSessionInvalidation {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformRbacSessionInvalidation.class);

    private final @NotNull IdentityService identities;

    private final @Nullable SessionControl sessions;

    /**
     * 创建 Platform Session 失效实现。
     *
     * @param identities IAM 身份管理服务
     * @param sessions Auth 安装的 Session 生命周期；应用未安装 Auth 时为空
     */
    public PlatformRbacSessionInvalidation(
            @NotNull IdentityService identities,
            @Nullable SessionControl sessions) {
        this.identities = Objects.requireNonNull(identities, "Platform 身份 Repository 不能为空");
        this.sessions = sessions;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull RbacDomain domain() {
        return PlatformRbacRepository.PLATFORM_DOMAIN;
    }

    /** {@inheritDoc} */
    @Override
    public void invalidate(
            @NotNull RbacScope scope,
            @NotNull Collection<@NotNull Long> principalIds) {
        if (!PlatformRbacRepository.PLATFORM.equals(scope)) {
            throw new IllegalArgumentException("Platform Session 失效只接受全局 Platform RBAC 范围");
        }
        if (sessions == null) {
            LOG.trace("跳过 Platform Session 失效 | reason=auth-not-installed");
            return;
        }
        List<Long> checkedIds = List.copyOf(
                new LinkedHashSet<>(
                        Objects.requireNonNull(principalIds, "PlatformPrincipal 标识不能为空")));
        if (checkedIds.isEmpty()) {
            LOG.trace("跳过 Platform Session 失效 | reason=no-principal");
            return;
        }
        SessionControl control = Objects.requireNonNull(sessions, "Auth 未安装 Session 控制器");
        Map<Long, List<Long>> accounts = identities.findLinkedAccountIds(checkedIds);
        List<Long> accountIds = accounts.values()
                .stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
        LOG.trace(
                "Platform Session 失效范围解析完成 | principalCount={} accountCount={}",
                checkedIds.size(),
                accountIds.size());
        control.invalidateAccounts(accountIds);
    }
}
