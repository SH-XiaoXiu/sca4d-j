package com.sca4d.iam.account.service;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.auth.SessionSnapshot;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.iam.account.AccountSecondFactorChange;
import com.sca4d.iam.account.AccountSecurity;
import com.sca4d.iam.account.AccountSecurityService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.passkey.PasskeySecurityDetails;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import com.sca4d.iam.recovery.IssuedRecoveryCodes;
import com.sca4d.iam.recovery.RecoveryCodeService;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 当前 Subject 的 Account 安全资料与登录 Session 自助管理实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAccountSecurityService implements AccountSecurityService {

    private final AccountService accountService;
    private final AccountIdentifierService identifierService;
    private final CredentialService credentialService;
    private final RecoveryCodeService recoveryCodeService;
    private final PasskeyRepository passkeyRepository;
    private final AuthenticationProcessService authentications;
    private final Authentication authentication;

    @Override
    public @NotNull List<AccountSecurity> accounts(@NotNull Long subjectId) {
        List<IamAccountEntity> accounts = accountService.findBySubject(subjectId);
        List<Long> accountIds = accounts.stream().map(IamAccountEntity::getId).toList();
        Map<Long, List<IamAccountIdentifierEntity>> identifiers = identifierService.findByAccounts(accountIds);
        Map<Long, List<IamCredentialEntity>> credentials = credentialService.findBySubjects(List.of(subjectId));
        Map<Long, List<IamCredentialEntity>> credentialsByAccount = credentials.getOrDefault(subjectId, List.of()).stream()
                .collect(Collectors.groupingBy(
                        IamCredentialEntity::getAccountId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, RecoveryCodeSummary> recoveryCodes = recoveryCodeService.findByAccounts(accountIds);
        Map<Long, List<PasskeySecurityDetails>> passkeys = passkeyRepository.findSecurityByAccounts(accountIds);
        // 五次固定数据库读取后只在内存按 Account 组装，Account 数量不会增加 SQL 次数。
        return accounts.stream()
                .map(account -> new AccountSecurity(
                        account,
                        identifiers.getOrDefault(account.getId(), List.of()),
                        credentialsByAccount.getOrDefault(account.getId(), List.of()),
                        passkeys.getOrDefault(account.getId(), List.of()),
                        recoveryCodes.get(account.getId())))
                .toList();
    }

    @Override
    public @NotNull AccountSecurity security(@NotNull Long accountId) {
        IamAccountEntity account = accountService.require(accountId);
        List<IamAccountIdentifierEntity> identifiers = identifierService.findByAccount(accountId);
        List<IamCredentialEntity> credentials = credentialService.findByAccount(accountId);
        Map<Long, RecoveryCodeSummary> recoveryCodes = recoveryCodeService.findByAccounts(List.of(accountId));
        Map<Long, List<PasskeySecurityDetails>> passkeys = passkeyRepository.findSecurityByAccounts(List.of(accountId));
        return new AccountSecurity(
                account,
                identifiers,
                credentials,
                passkeys.getOrDefault(accountId, List.of()),
                recoveryCodes.get(accountId));
    }

    @Override
    public @NotNull List<SessionSnapshot> sessions(
            @NotNull Long subjectId, @NotNull Long accountId) {
        requireOwnedAccount(subjectId, accountId);
        // 一个 Account-Session 只读取一次 Redis；终端列表从同一值的内存副本裁剪，不逐 Token 回源。
        return authentication.sessions(accountId);
    }

    @Override
    public @NotNull List<SessionSnapshot> managedSessions(@NotNull Long accountId) {
        accountService.require(accountId);
        return authentication.sessions(accountId);
    }

    @Override
    public void endSession(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String sessionId,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        requireOwnedAccount(subject.getSubjectId(), targetAccountId);
        // 高风险撤销先复核并单次消费当前 Session Account 的 STEP_UP，失败后不结束目标登录 Session。
        authentications.authorizeStepUp(
                authenticationId, browserHash, subject, authenticatedAccount);
        authentication.endSession(targetAccountId, sessionId);
        log.info(
                "当前用户登录会话已结束 | subjectId={} authenticatedAccountId={} targetAccountId={} sessionId={}",
                subject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                sessionId);
    }

    @Override
    public void endManagedSession(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String sessionId,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        accountService.require(targetAccountId);
        authentications.authorizeStepUp(
                authenticationId, browserHash, authenticatedSubject, authenticatedAccount);
        authentication.endSession(targetAccountId, sessionId);
        log.info(
                "管理员账号登录会话已结束 | authenticatedSubjectId={} authenticatedAccountId={} targetAccountId={} sessionId={}",
                authenticatedSubject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                sessionId);
    }

    @Override
    public void endManagedSessions(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        accountService.require(targetAccountId);
        authentications.authorizeStepUp(
                authenticationId, browserHash, authenticatedSubject, authenticatedAccount);
        authentication.endSessions(targetAccountId);
        log.info(
                "管理员已结束账号全部登录会话 | authenticatedSubjectId={} authenticatedAccountId={} targetAccountId={}",
                authenticatedSubject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId);
    }

    @Override
    @DistributedLock(key = "'subject:' + #subject.subjectId")
    @Transactional
    public @NotNull AccountSecondFactorChange changeSecondFactorRequirement(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            boolean required,
            long version,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        // Subject 锁内读取 Account；STEP_UP 之后只允许修改调用方刚读取的版本。
        requireOwnedAccount(subject.getSubjectId(), targetAccountId);
        authentications.authorizeStepUp(
                authenticationId, browserHash, subject, authenticatedAccount);
        IamAccountEntity account = accountService.require(targetAccountId);
        IssuedRecoveryCodes issued = null;
        if (required && !Boolean.TRUE.equals(account.getSecondFactorRequired())) {
            // 恢复码摘要和 Account 状态处于同一数据库事务；Account 更新失败时新集合一起回滚。
            issued = recoveryCodeService.issue(targetAccountId);
        } else {
            recoveryCodeService.revoke(targetAccountId);
        }
        IamAccountEntity updated = accountService.changeSecondFactorRequirement(targetAccountId, required, version);
        // 安全设置变化后旧 Session 不得继续使用此前的认证因素状态。
        log.info(
                "当前用户二次验证要求已修改 | subjectId={} authenticatedAccountId={} targetAccountId={} required={} version={}",
                subject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                required,
                updated.getVersion());
        return new AccountSecondFactorChange(
                updated,
                issued == null ? null : issued.getSummary(),
                issued == null ? List.of() : issued.getCodes());
    }

    @Override
    @DistributedLock(key = "'account:' + #targetAccountId")
    @Transactional
    public @NotNull AccountSecondFactorChange changeManagedSecondFactorRequirement(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            boolean required,
            long version,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        IamAccountEntity account = accountService.require(targetAccountId);
        authentications.authorizeStepUp(
                authenticationId, browserHash, authenticatedSubject, authenticatedAccount);
        IssuedRecoveryCodes issued = null;
        if (required && !Boolean.TRUE.equals(account.getSecondFactorRequired())) {
            issued = recoveryCodeService.issue(targetAccountId);
        } else {
            recoveryCodeService.revoke(targetAccountId);
        }
        IamAccountEntity updated = accountService.changeSecondFactorRequirement(targetAccountId, required, version);
        log.info(
                "管理员账号 MFA 要求已修改 | authenticatedSubjectId={} authenticatedAccountId={} targetAccountId={} required={} version={}",
                authenticatedSubject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                required,
                updated.getVersion());
        return new AccountSecondFactorChange(
                updated,
                issued == null ? null : issued.getSummary(),
                issued == null ? List.of() : issued.getCodes());
    }

    @Override
    @DistributedLock(key = "'account:' + #targetAccountId")
    @Transactional
    public @NotNull AccountSecondFactorChange reissueManagedRecoveryCodes(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            long version,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        IamAccountEntity account = accountService.require(targetAccountId);
        if (!Boolean.TRUE.equals(account.getSecondFactorRequired())) {
            throw new BizException(IamErrorCode.ACCOUNT_RECOVERY_CODES_UNAVAILABLE);
        }
        authentications.authorizeStepUp(
                authenticationId, browserHash, authenticatedSubject, authenticatedAccount);
        IssuedRecoveryCodes issued = recoveryCodeService.issue(targetAccountId);
        IamAccountEntity updated = accountService.changeSecondFactorRequirement(targetAccountId, true, version);
        log.info(
                "管理员账号恢复码已重新生成 | authenticatedSubjectId={} authenticatedAccountId={} targetAccountId={} version={}",
                authenticatedSubject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                updated.getVersion());
        return new AccountSecondFactorChange(updated, issued.getSummary(), issued.getCodes());
    }

    @Override
    @Transactional
    public @NotNull Long bindManagedPassword(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String identifier,
            @NotNull PasswordCredential credential,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        accountService.require(targetAccountId);
        authentications.authorizeStepUp(
                authenticationId, browserHash, authenticatedSubject, authenticatedAccount);
        identifierService.createUsername(targetAccountId, identifier);
        Long credentialId = credentialService.bind(targetAccountId, credential);
        authentication.endSessions(targetAccountId);
        log.info(
                "管理员账号密码凭证已绑定 | authenticatedSubjectId={} authenticatedAccountId={} targetAccountId={} credentialId={}",
                authenticatedSubject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                credentialId);
        return credentialId;
    }

    @Override
    @Transactional
    public void replaceManagedPassword(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long credentialId,
            long credentialVersion,
            @NotNull PasswordCredential credential,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        accountService.require(targetAccountId);
        authentications.authorizeStepUp(
                authenticationId, browserHash, authenticatedSubject, authenticatedAccount);
        credentialService.replace(targetAccountId, credentialId, credentialVersion, credential);
        authentication.endSessions(targetAccountId);
        log.info(
                "管理员账号密码凭证已重置 | authenticatedSubjectId={} authenticatedAccountId={} targetAccountId={} credentialId={} version={}",
                authenticatedSubject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                credentialId,
                credentialVersion + 1);
    }

    /**
     * 确认 Account 属于当前 Subject，避免账号安全资源泄露其他 Subject 的存在。
     *
     * @param subjectId 当前登录 Subject 主键
     * @param accountId 路由中的 Account 主键
     * @return 当前 Subject 名下未删除 Account
     * @throws BizException Account 不存在或不属于当前 Subject 时抛出
     */
    private @NotNull IamAccountEntity requireOwnedAccount(
            @NotNull Long subjectId, @NotNull Long accountId) {
        IamAccountEntity account = accountService.require(accountId);
        if (!subjectId.equals(account.getSubjectId())) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }
}
