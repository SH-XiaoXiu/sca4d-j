package com.sca4d.iam.account.service;

import com.sca4d.common.auth.Authentication;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.subject.SubjectService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 停用和删除 Account，同时保持现有 Principal 仍有可达登录入口。 */
@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final AccountService accountService;
    private final SubjectService subjectService;
    private final IamRepository iamRepository;
    private final Authentication authentication;

    @Transactional
    @DistributedLock(key = "'account:' + #accountId")
    public @NotNull IamAccountEntity changeStatus(
            @NotNull Long accountId, @NotNull AccountStatus status, long version) {
        IamAccountEntity account = accountService.require(accountId);
        if (status == AccountStatus.DISABLED
                && iamRepository.hasUnreachableActivePrincipal(account.getSubjectId(), accountId)) {
            throw new BizException(IamErrorCode.ACCOUNT_ACCESS_WOULD_BE_LOST);
        }
        iamRepository.updateAccountStatus(accountId, status, version);
        if (status == AccountStatus.DISABLED) {
            authentication.endSessions(accountId);
        }
        return accountService.require(accountId);
    }

    @Transactional
    @DistributedLock(key = "'account:' + #accountId")
    public void delete(@NotNull Long accountId, long version) {
        IamAccountEntity account = accountService.require(accountId);
        if (!Long.valueOf(version).equals(account.getVersion())) {
            throw new BizException(IamErrorCode.ACCOUNT_CHANGED);
        }
        if (iamRepository.hasUnreachableActivePrincipal(account.getSubjectId(), accountId)) {
            throw new BizException(IamErrorCode.ACCOUNT_ACCESS_WOULD_BE_LOST);
        }
        long remaining = accountService.findBySubject(account.getSubjectId()).stream()
                .filter(candidate -> !candidate.getId().equals(accountId))
                .count();
        authentication.endSessions(accountId);
        if (remaining == 0) {
            IamSubjectEntity subject = subjectService.require(account.getSubjectId());
            subjectService.delete(subject.getId(), subject.getVersion());
            return;
        }
        iamRepository.isolateAccount(accountId, version);
    }
}
