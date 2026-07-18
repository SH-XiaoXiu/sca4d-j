package com.sca4d.iam.account;

import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.iam.passkey.PasskeySecurityDetails;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 一个 Account 及其非敏感 Identifier、Credential 安全表示。 */
public class AccountSecurity {

    private final IamAccountEntity account;
    private final List<IamAccountIdentifierEntity> identifiers;
    private final List<IamCredentialEntity> credentials;
    private final List<PasskeySecurityDetails> passkeyCredentials;
    private final RecoveryCodeSummary recoveryCodeSummary;

    /**
     * 创建当前用户账号安全聚合。
     *
     * @param account Subject 名下的登录 Account
     * @param identifiers 当前 Account 的登录标识
     * @param credentials 当前 Account 的非敏感认证材料
     * @param passkeyCredentials 当前 Account 的 Passkey 展示名称、备份状态和使用时间
     * @param recoveryCodeSummary 当前恢复码集合状态；尚未签发时为空
     */
    public AccountSecurity(
            @NotNull IamAccountEntity account,
            @NotNull List<IamAccountIdentifierEntity> identifiers,
            @NotNull List<IamCredentialEntity> credentials,
            @NotNull List<PasskeySecurityDetails> passkeyCredentials,
            @Nullable RecoveryCodeSummary recoveryCodeSummary) {
        this.account = account;
        this.identifiers = List.copyOf(identifiers);
        this.credentials = List.copyOf(credentials);
        this.passkeyCredentials = List.copyOf(passkeyCredentials);
        this.recoveryCodeSummary = recoveryCodeSummary;
    }

    /** @return Subject 名下的登录 Account */
    public @NotNull IamAccountEntity getAccount() {
        return account;
    }

    /** @return 当前 Account 的不可变 Identifier 集合 */
    public @NotNull List<IamAccountIdentifierEntity> getIdentifiers() {
        return identifiers;
    }

    /** @return 当前 Account 的不可变 Credential 集合 */
    public @NotNull List<IamCredentialEntity> getCredentials() {
        return credentials;
    }

    /** @return 当前 Account 的不可变 Passkey 非敏感资料集合 */
    public @NotNull List<PasskeySecurityDetails> getPasskeyCredentials() {
        return passkeyCredentials;
    }

    /** @return 当前恢复码集合状态；尚未签发时为空 */
    public @Nullable RecoveryCodeSummary getRecoveryCodeSummary() {
        return recoveryCodeSummary;
    }
}
