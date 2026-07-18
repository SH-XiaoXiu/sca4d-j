package com.sca4d.iam.account.view;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecurity;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.identifier.view.AccountIdentifierView;
import com.sca4d.iam.passkey.view.PasskeyCredentialView;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/** 当前 Subject 名下一个 Account 的安全设置和认证入口表示。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountSecurityView {

    private Long accountId;
    private AccountScopeType scopeType;
    private Long scopeId;
    private AccountStatus status;
    private boolean secondFactorRequired;
    private Long version;
    private OffsetDateTime recoveryCodesIssuedAt;
    private int recoveryCodesRemaining;
    private List<AccountIdentifierView> identifiers;
    private List<AccountCredentialView> credentials;
    private List<PasskeyCredentialView> passkeyCredentials;

    /** @return Account、Identifier 和 Credential 的安全响应表示 */
    public static @NotNull AccountSecurityView from(@NotNull AccountSecurity security) {
        IamAccountEntity account = security.getAccount();
        RecoveryCodeSummary recoveryCodes = security.getRecoveryCodeSummary();
        return new AccountSecurityView(
                account.getId(),
                account.getScopeType(),
                account.getScopeId(),
                account.getStatus(),
                Boolean.TRUE.equals(account.getSecondFactorRequired()),
                account.getVersion(),
                recoveryCodes == null ? null : recoveryCodes.getIssuedAt(),
                recoveryCodes == null ? 0 : recoveryCodes.getRemainingCount(),
                security.getIdentifiers().stream()
                        .map(AccountIdentifierView::from)
                        .toList(),
                security.getCredentials().stream()
                        .map(AccountCredentialView::from)
                        .toList(),
                security.getPasskeyCredentials().stream()
                        .map(PasskeyCredentialView::from)
                        .toList());
    }
}
