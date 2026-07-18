package com.sca4d.authentication.recovery.service;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.authentication.recovery.AccountRecoveryService;
import com.sca4d.authentication.recovery.ConsumedAccountRecovery;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.subject.SubjectLock;
import com.sca4d.iam.subject.SubjectService;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Objects;

/** 使用已完成的 RECOVERY 认证替换 Account 密码并结束全部旧登录会话。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAccountRecoveryService implements AccountRecoveryService {

    private final AuthenticationProcessService authentications;
    private final SubjectLock subjectLock;
    private final SubjectService subjectService;
    private final AccountService accountService;
    private final CredentialService credentialService;
    private final Authentication authentication;
    private final TransactionOperations transactions;

    @Override
    public void replacePassword(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull PasswordCredential password) {
        // 认证先完成单次消费；密码写入失败时不恢复，防止同一恢复码重复修改密码。
        ConsumedAccountRecovery recovery = authentications.consumeRecovery(authenticationId, browserHash);
        subjectLock.execute(recovery.getSubject().getSubjectId(), () -> {
            transactions.execute(status -> {
                // 取得锁后在事务内重新读取 Account 与 Subject，拒绝认证完成后发生的状态或归属变化。
                IamAccountEntity account = accountService.require(recovery.getAccount().getAccountId());
                IamSubjectEntity subject = subjectService.require(recovery.getSubject().getSubjectId());
                if (!account.getSubjectId().equals(subject.getId())
                        || !account.getSubjectId().equals(recovery.getAccount().getSubjectId())
                        || account.getScopeType() != recovery.getAccount().getScopeType()
                        || !Objects.equals(account.getScopeId(), recovery.getAccount().getScopeId())
                        || account.getStatus() != AccountStatus.ACTIVE
                        || subject.getStatus() != SubjectStatus.ACTIVE
                        || !Boolean.TRUE.equals(account.getSecondFactorRequired())) {
                    throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
                }
                IamCredentialEntity passwordCredential = credentialService.findBySubject(subject.getId()).stream()
                        .filter(credential -> credential.getAccountId().equals(account.getId()))
                        .filter(credential -> credential.getCredentialType() == CredentialType.PASSWORD)
                        .findFirst()
                        .orElseThrow(() -> new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED));
                // PASSWORD Scheme 原子推进 Credential 版本；更新成功后立即结束目标 Account 的全部旧会话。
                credentialService.replace(
                        account.getId(),
                        passwordCredential.getId(),
                        passwordCredential.getVersion(),
                        password);
                authentication.endSessions(account.getId());
                log.info(
                        "账号恢复密码替换已写入事务，现有登录会话已结束 | subjectId={} accountId={} credentialId={} version={}",
                        subject.getId(),
                        account.getId(),
                        passwordCredential.getId(),
                        passwordCredential.getVersion() + 1);
                return null;
            });
            return null;
        });
    }
}
