package com.sca4d.iam.base;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.PrincipalLoader;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 为 platform realm 的身份读取当前可选择的 `PlatformPrincipal`。 */
public class PlatformPrincipalLoader implements PrincipalLoader {

    private final PlatformIdentityService identities;

    /**
     * 创建 Platform Principal 装载器。
     *
     * @param identities Platform 身份服务
     */
    public PlatformPrincipalLoader(@NotNull PlatformIdentityService identities) {
        this.identities = Objects.requireNonNull(identities, "平台身份服务不能为空");
    }

    @Override
    @CheckReturnValue
    public boolean supports(@NotNull SessionIdentity identity) {
        return PlatformIdentityService.PLATFORM_REALM.equals(identity.account().realmKey());
    }

    /**
     * 读取当前 Account 关联且可用的全部 `PlatformPrincipal`。
     *
     * @throws IllegalArgumentException 收到非 platform realm 身份时抛出
     * @throws BusinessException Account 或 Principal 已不可用时抛出
     */
    @Override
    @CheckReturnValue
    public @NotNull @Unmodifiable List<? extends @NotNull Principal> load(
            @NotNull SessionIdentity identity) {
        SessionIdentity checked = Objects.requireNonNull(identity, "SessionIdentity 不能为空");
        if (!supports(checked)) {
            throw new IllegalArgumentException("Platform Principal Loader 收到非 platform realm 身份");
        }
        return identities.activePrincipals(checked.account().accountId());
    }
}
