package com.sca4d.iam.identity;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/** 使用 IAM 的标识规范化和身份事实实现 `AccountDirectory`。 */
@ApiStatus.Internal
public final class IamAccountDirectory implements AccountDirectory {

    private final @NotNull IdentityService identities;

    /** @param identities 负责标识解析和 Account 状态校验的 IAM 身份服务 */
    public IamAccountDirectory(@NotNull IdentityService identities) {
        this.identities = Objects.requireNonNull(identities, "身份服务不能为空");
    }

    @Override
    public @NotNull Optional<Account> resolve(
            @NotNull RealmKey realm,
            @NotNull AccountIdentifierType type,
            @NotNull String value) {
        return identities.resolveAccount(realm, type, value).flatMap(identities::findAccount);
    }

    @Override
    public @NotNull Account activeById(long accountId) {
        return identities.activeAccountById(accountId);
    }
}
