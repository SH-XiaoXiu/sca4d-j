package com.sca4d.auth.session;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.SessionIdentityLoader;
import com.sca4d.core.api.identity.Account;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 选择唯一负责指定 Account 的 {@link SessionIdentityLoader} 并校验返回结果。 */
public final class SessionIdentityLoadService {

    private final List<SessionIdentityLoader> loaders;

    /**
     * 创建 Session 身份读取服务。
     *
     * @param loaders 应用安装的 Session 身份 Loader
     */
    public SessionIdentityLoadService(@NotNull List<@NotNull SessionIdentityLoader> loaders) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "Session 身份 Loader 不能为空"));
    }

    /**
     * 从唯一认领 Account 的 Loader 读取当前身份。
     *
     * @param account 待读取身份的 Account
     * @return 当前 Account、Subject 和全部可用 Principal
     * @throws IllegalStateException 没有 Loader、多个 Loader 或 Loader 返回其他 Account 时抛出
     */
    @CheckReturnValue
    public @NotNull SessionIdentity load(@NotNull Account account) {
        Account checkedAccount = Objects.requireNonNull(account, "Account 不能为空");
        List<SessionIdentityLoader> matched = loaders.stream()
                .filter(loader -> loader.supports(checkedAccount))
                .toList();
        if (matched.size() != 1) {
            throw new IllegalStateException(
                    matched.isEmpty()
                            ? "没有 SessionIdentityLoader 认领 Account"
                            : "多个 SessionIdentityLoader 认领 Account");
        }
        SessionIdentity identity = Objects.requireNonNull(
                matched.getFirst().load(checkedAccount),
                "SessionIdentityLoader 不能返回 null");
        if (checkedAccount.accountId() != identity.account().accountId()) {
            throw new IllegalStateException("SessionIdentityLoader 返回了其他 Account");
        }
        return identity;
    }
}
