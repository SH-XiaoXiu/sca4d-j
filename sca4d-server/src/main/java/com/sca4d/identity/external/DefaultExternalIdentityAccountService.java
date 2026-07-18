package com.sca4d.identity.external;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.repository.IdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 当前用户 ExternalIdentity 查询与安全解除关联实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultExternalIdentityAccountService implements ExternalIdentityAccountService {

    private final AccountService accounts;
    private final CredentialService credentials;
    private final ExternalIdentityRepository identities;
    private final IdentityProviderRepository identityProviders;
    private final AuthenticationProcessService authentications;
    private final Authentication authentication;

    @Override
    public @NotNull List<ExternalIdentityDetails> identities(
            @NotNull Long subjectId, @NotNull Long accountId) {
        requireOwnedAccount(subjectId, accountId);
        return identities.findByAccount(accountId);
    }

    @Override
    public @NotNull List<IdentityProvider> providers(
            @NotNull Long subjectId, @NotNull Long accountId) {
        IamAccountEntity account = requireOwnedAccount(subjectId, accountId);
        IdentityProviderScope scope = new IdentityProviderScope(
                account.getScopeType(), account.getScopeId());
        return identityProviders.findProviders(scope).stream()
                .filter(provider -> provider.getStatus() == IdentityProviderStatus.ACTIVE)
                .toList();
    }

    @Override
    @DistributedLock(key = "'subject:' + #subject.subjectId")
    @Transactional
    public void delete(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long identityId,
            @NotNull Long version,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        IamAccountEntity targetAccount = accounts.require(targetAccountId);
        ExternalIdentityLink identity = identities.findLink(identityId);
        if (!subject.getSubjectId().equals(targetAccount.getSubjectId())
                || identity == null
                || !targetAccountId.equals(identity.getIdentity().getAccountId())) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        boolean localPrimaryMethod = credentials.findBySubject(subject.getSubjectId()).stream()
                .anyMatch(credential -> targetAccountId.equals(credential.getAccountId())
                        && credential.getStatus() == CredentialStatus.ACTIVE
                        && (credential.getCredentialType() == CredentialType.PASSWORD
                                || credential.getCredentialType() == CredentialType.PASSKEY));
        // 停用 Provider 的绑定已经不能登录；解除它不会减少 Account 当前可用的认证方式。
        if (!localPrimaryMethod
                && identity.getProviderStatus() == IdentityProviderStatus.ACTIVE
                && !identities.hasOtherActiveLoginIdentity(targetAccountId, identityId)) {
            throw new BizException(
                    IdentityProviderErrorCode.EXTERNAL_IDENTITY_REMOVAL_WOULD_LOCK_ACCOUNT);
        }
        authentications.authorizeStepUp(
                authenticationId, browserHash, subject, authenticatedAccount);
        identities.delete(identityId, targetAccountId, version);
        authentication.endSessions(targetAccountId);
        log.info(
                "ExternalIdentity 删除已写入事务，现有登录会话已结束 | subjectId={} authenticatedAccountId={} targetAccountId={} identityId={}",
                subject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                identityId);
    }

    /**
     * 读取属于当前 Subject 的 Account，避免查询结果暴露其他用户的账号和 Provider。
     *
     * @param subjectId 当前登录 Subject 主键
     * @param accountId 路由中的目标 Account 主键
     * @return 当前 Subject 名下的 Account
     * @throws BizException Account 不存在或不属于当前 Subject 时抛出
     */
    private @NotNull IamAccountEntity requireOwnedAccount(
            @NotNull Long subjectId, @NotNull Long accountId) {
        IamAccountEntity account = accounts.require(accountId);
        if (!subjectId.equals(account.getSubjectId())) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }
}
