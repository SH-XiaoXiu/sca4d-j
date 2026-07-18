package com.sca4d.identity.external;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.identity.IdentityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 查询和解除当前 Subject 名下 Account 的 ExternalIdentity 关联。 */
public interface ExternalIdentityAccountService {

    /** @return 当前 Subject 名下指定 Account 的全部 ExternalIdentity */
    @NotNull
    List<ExternalIdentityDetails> identities(
            @NotNull Long subjectId, @NotNull Long accountId);

    /** @return 当前 Subject 名下指定 Account 可以关联的全部 ACTIVE IdentityProvider */
    @NotNull
    List<IdentityProvider> providers(@NotNull Long subjectId, @NotNull Long accountId);

    /** 使用 STEP_UP 解除关联并结束目标 Account 的全部旧 Session。 */
    void delete(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long identityId,
            @NotNull Long version,
            @NotNull String authenticationId,
            @NotNull String browserHash);
}
