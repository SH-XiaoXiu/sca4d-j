package com.sca4d.iam.account.service;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.file.FileResource;
import com.sca4d.file.FileService;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.ProvisionedAccount;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.profile.DefaultProfileAvatar;
import com.sca4d.iam.profile.repository.DefaultProfileRepository;
import com.sca4d.iam.subject.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 创建新 Subject 的首个 Account、DEFAULT Principal 和 DEFAULT Profile。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAccountProvisioningService implements AccountProvisioningService {

    private final SubjectService subjectService;
    private final AccountService accountService;
    private final AccountIdentifierService accountIdentifierService;
    private final CredentialService credentialService;
    private final ContactService contactService;
    private final PrincipalService principalService;
    private final DefaultProfileRepository defaultProfileRepository;
    private final FileService fileService;

    @Transactional
    @Override
    public <C> @NotNull ProvisionedAccount create(
            @NotNull String username,
            @NotNull C credential,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull String displayName,
            @Nullable String phone,
            @Nullable String email,
            @Nullable Long avatarFileId,
            @Nullable String avatarUploadGrant,
            @Nullable Long actorPrincipalId) {
        if (!StringUtils.hasText(displayName)) {
            throw new BizException(IamErrorCode.SUBJECT_DISPLAY_NAME_REQUIRED);
        }
        String checkedDisplayName = displayName.trim();
        boolean hasAvatarFile = avatarFileId != null;
        boolean hasAvatarGrant = StringUtils.hasText(avatarUploadGrant);
        if (hasAvatarFile != hasAvatarGrant) {
            throw new BizException(IamErrorCode.DEFAULT_PROFILE_AVATAR_BINDING_INCOMPLETE);
        }
        if (hasAvatarFile && actorPrincipalId == null) {
            throw new BizException(IamErrorCode.DEFAULT_PROFILE_AVATAR_PRINCIPAL_REQUIRED);
        }
        if (hasAvatarFile) {
            FileResource avatar = fileService.requireReadyFile(avatarFileId, actorPrincipalId);
            DefaultProfileAvatar.requireSupportedFile(avatar.getContentType(), avatar.getSizeBytes());
        }

        IamSubjectEntity subject = subjectService.create(checkedDisplayName);
        contactService.replace(subject.getId(), ContactType.PHONE, phone);
        contactService.replace(subject.getId(), ContactType.EMAIL, email);
        IamAccountEntity account = accountService.create(subject.getId(), scopeType, scopeId);
        accountIdentifierService.createUsername(account.getId(), username);
        credentialService.bind(account.getId(), credential);
        IamPrincipalEntity defaultPrincipal = principalService.createDefault(subject.getId(), checkedDisplayName);
        if (hasAvatarFile) {
            fileService.consumeFile(
                    avatarFileId,
                    avatarUploadGrant,
                    DefaultProfileAvatar.FILE_SCENE,
                    null,
                    actorPrincipalId);
        }
        defaultProfileRepository.create(defaultPrincipal.getId(), avatarFileId);
        log.info(
                "Account 创建已写入事务 | subjectId={} principalId={} accountId={} scopeType={} avatarFileId={}",
                subject.getId(),
                defaultPrincipal.getId(),
                account.getId(),
                scopeType,
                avatarFileId);
        return new ProvisionedAccount()
                .setSubject(subject)
                .setDefaultPrincipal(defaultPrincipal)
                .setAccount(account);
    }

    @Transactional
    @Override
    public @NotNull ProvisionedAccount createExternal(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull String displayName) {
        IamSubjectEntity subject = subjectService.create(displayName);
        IamAccountEntity account = accountService.create(subject.getId(), scopeType, scopeId);
        IamPrincipalEntity defaultPrincipal = principalService.createDefault(subject.getId(), displayName);
        defaultProfileRepository.create(defaultPrincipal.getId(), null);
        log.info(
                "ExternalIdentity Account 创建已写入事务 | subjectId={} principalId={} accountId={} scopeType={} scopeId={}",
                subject.getId(),
                defaultPrincipal.getId(),
                account.getId(),
                scopeType,
                scopeId);
        return new ProvisionedAccount()
                .setSubject(subject)
                .setDefaultPrincipal(defaultPrincipal)
                .setAccount(account);
    }
}
