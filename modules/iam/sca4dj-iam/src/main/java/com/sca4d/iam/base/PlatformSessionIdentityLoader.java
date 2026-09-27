package com.sca4d.iam.base;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.SessionIdentityLoader;
import com.sca4d.core.api.authentication.SessionIdentityUnavailableException;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 装载平台 Account 当前可取得的具体 IAM 身份。 */
public class PlatformSessionIdentityLoader implements SessionIdentityLoader {

    private final @NotNull PlatformIdentityService identities;

    /**
     * 创建 Platform Session 身份装载器。
     *
     * @param identities Platform 身份服务
     */
    public PlatformSessionIdentityLoader(@NotNull PlatformIdentityService identities) {
        this.identities = Objects.requireNonNull(identities, "平台身份服务不能为空");
    }

    /** {@inheritDoc} */
    @Override
    @CheckReturnValue
    public boolean supports(@NotNull Account account) {
        return PlatformIdentityService.PLATFORM_REALM.equals(account.realmKey());
    }

    /**
     * 重新读取可用 platform realm Account 和 Subject。
     *
     * @throws SessionIdentityUnavailableException Account 或 Subject 已停用时抛出
     */
    @Override
    @CheckReturnValue
    public @NotNull SessionIdentity load(@NotNull Account account) {
        Account checked = Objects.requireNonNull(account, "Account 不能为空");
        if (!supports(checked)) {
            throw new IllegalArgumentException("Platform Session Loader 收到非 platform realm Account");
        }
        try {
            Account current = identities.activeAccountById(checked.accountId());
            return new SessionIdentity(identities.activeSubjectById(current.subjectId()), current);
        } catch (BusinessException exception) {
            throw new SessionIdentityUnavailableException(exception);
        }
    }
}
