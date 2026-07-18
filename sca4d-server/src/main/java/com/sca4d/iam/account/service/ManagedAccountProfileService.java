package com.sca4d.iam.account.service;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.SessionService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.ManagedAccountProfile;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.profile.service.DefaultProfileService;
import com.sca4d.iam.repository.IamRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 读取和修改平台账号关联的 Subject 通用资料。 */
@Service
@RequiredArgsConstructor
public class ManagedAccountProfileService {

    private final AccountService accountService;
    private final DefaultProfileService defaultProfileService;
    private final ContactService contactService;
    private final IamRepository iamRepository;
    private final AuthenticationProcessService authentications;
    private final Authentication authentication;
    private final SessionService sessionService;

    public @NotNull ManagedAccountProfile profile(@NotNull Long accountId) {
        IamAccountEntity account = accountService.require(accountId);
        IamSubjectEntity subject = iamRepository.findSubject(account.getSubjectId());
        if (subject == null) {
            throw new IllegalStateException("Account 对应 Subject 不存在: " + accountId);
        }
        IamContactEntity phone = contactService.current(subject.getId(), ContactType.PHONE);
        IamContactEntity email = contactService.current(subject.getId(), ContactType.EMAIL);
        return new ManagedAccountProfile(
                accountId,
                subject.getId(),
                subject.getDisplayName(),
                phone == null ? null : phone.getValue(),
                email == null ? null : email.getValue(),
                defaultProfileService.require(subject.getId()),
                subject.getVersion());
    }

    @Transactional
    @DistributedLock(key = "'subject:' + #targetSubjectId")
    public void update(
            @NotNull Subject authenticatedSubject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long targetSubjectId,
            @NotNull String displayName,
            @Nullable String phone,
            @Nullable String email,
            long subjectVersion,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        IamAccountEntity account = accountService.require(targetAccountId);
        if (!targetSubjectId.equals(account.getSubjectId())) {
            throw new IllegalStateException("Account 与 Subject 不匹配: " + targetAccountId);
        }
        authentications.authorizeStepUp(
                authenticationId, browserHash, authenticatedSubject, authenticatedAccount);
        IamContactEntity currentEmail = contactService.current(account.getSubjectId(), ContactType.EMAIL);
        String oldEmail = currentEmail == null ? null : currentEmail.getValue();
        iamRepository.updateSubject(account.getSubjectId(), displayName.trim(), subjectVersion);
        contactService.replace(account.getSubjectId(), ContactType.PHONE, phone);
        contactService.replace(account.getSubjectId(), ContactType.EMAIL, email);
        String normalizedOldEmail = oldEmail == null || oldEmail.isBlank()
                ? null
                : oldEmail.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedEmail = email == null || email.isBlank()
                ? null
                : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Objects.equals(normalizedOldEmail, normalizedEmail)) {
            for (IamAccountEntity subjectAccount : accountService.findBySubject(account.getSubjectId())) {
                authentication.endSessions(subjectAccount.getId());
            }
        } else {
            sessionService.refreshAfterCommit(List.of(account.getSubjectId()));
        }
    }
}
