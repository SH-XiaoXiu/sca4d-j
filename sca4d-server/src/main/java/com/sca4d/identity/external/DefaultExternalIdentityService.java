package com.sca4d.identity.external;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.subject.SubjectLock;
import com.sca4d.iam.subject.SubjectService;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.repository.IdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Objects;

/** 校验 Provider 与 Account 作用域，并在 Subject 锁内创建或更新 ExternalIdentity。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultExternalIdentityService implements ExternalIdentityService {

    private final IdentityProviderRepository identityProviderRepository;
    private final ExternalIdentityRepository repository;
    private final AccountService accountService;
    private final SubjectService subjectService;
    private final SubjectLock subjectLock;
    private final TransactionOperations transactions;

    @Override
    public @Nullable ExternalIdentityVerification authenticate(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull ExternalIdentityProfile profile) {
        ExternalIdentityProfile checkedProfile = profile(profile);
        IdentityProvider provider = requireActiveProvider(scope, providerId);
        ExternalIdentityLink link = repository.findLink(providerId, checkedProfile.getExternalSubject());
        if (link == null) {
            return null;
        }
        ExternalIdentityVerification verification = verification(provider, link);
        return subjectLock.execute(verification.getSubject().getSubjectId(), () -> {
            IdentityProvider currentProvider = requireActiveProvider(scope, providerId);
            ExternalIdentityLink current = repository.findLink(link.getIdentity().getIdentityId());
            if (current == null
                    || !link.getIdentity().getVersion().equals(current.getIdentity().getVersion())) {
                throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
            }
            verification(currentProvider, current);
            ExternalIdentityLink updated = repository.recordAuthentication(
                    current.getIdentity().getIdentityId(),
                    providerId,
                    current.getIdentity().getVersion(),
                    checkedProfile);
            log.info(
                    "ExternalIdentity 认证资料已更新 | identityId={} providerId={} accountId={} subjectId={}",
                    updated.getIdentity().getIdentityId(),
                    providerId,
                    updated.getAccount().getId(),
                    updated.getSubject().getId());
            return verification(currentProvider, updated);
        });
    }

    @Override
    public @NotNull ExternalIdentityVerification link(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull Long accountId,
            @NotNull ExternalIdentityProfile profile) {
        ExternalIdentityProfile checkedProfile = profile(profile);
        requireActiveProvider(scope, providerId);
        IamAccountEntity account = accountService.require(accountId);
        return subjectLock.execute(account.getSubjectId(), () -> {
            return transactions.execute(status -> {
                IdentityProvider provider = requireActiveProvider(scope, providerId);
                IamAccountEntity currentAccount = accountService.require(accountId);
                IamSubjectEntity currentSubject = subjectService.require(currentAccount.getSubjectId());
                if (currentAccount.getStatus() != AccountStatus.ACTIVE
                        || currentSubject.getStatus() != SubjectStatus.ACTIVE
                        || !currentAccount.getSubjectId().equals(account.getSubjectId())
                        || currentAccount.getScopeType() != scope.getScopeType()
                        || !Objects.equals(currentAccount.getScopeId(), scope.getScopeId())) {
                    throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
                }
                ExternalIdentityLink existing = repository.findLink(providerId, checkedProfile.getExternalSubject());
                ExternalIdentityLink linked;
                if (existing == null) {
                    linked = repository.create(providerId, accountId, checkedProfile);
                    log.info(
                            "ExternalIdentity 绑定已写入事务 | identityId={} providerId={} accountId={} subjectId={}",
                            linked.getIdentity().getIdentityId(),
                            providerId,
                            accountId,
                            currentSubject.getId());
                } else if (existing.getAccount().getId().equals(accountId)) {
                    linked = repository.recordAuthentication(
                            existing.getIdentity().getIdentityId(),
                            providerId,
                            existing.getIdentity().getVersion(),
                            checkedProfile);
                } else {
                    throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
                }
                return verification(provider, linked);
            });
        });
    }

    @Override
    public @NotNull ExternalIdentityVerification requireCurrent(
            @NotNull Long identityId, @NotNull Long identityVersion) {
        ExternalIdentityLink link = repository.findLink(identityId);
        if (link == null || !identityVersion.equals(link.getIdentity().getVersion())) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
        }
        return verification(null, link);
    }

    /** @return 长度、空值和可信邮箱组合均有效的上游资料 */
    private @NotNull ExternalIdentityProfile profile(@NotNull ExternalIdentityProfile profile) {
        String externalSubject = profile.getExternalSubject();
        String displayName = profile.getDisplayName();
        String email = profile.getEmail();
        String avatarUri = profile.getAvatarUri();
        if (externalSubject == null
                || externalSubject.isBlank()
                || externalSubject.length() > 512
                || (displayName != null && displayName.length() > 256)
                || (email != null && (email.isBlank() || email.length() > 256))
                || (avatarUri != null && (avatarUri.isBlank() || avatarUri.length() > 1024))
                || (Boolean.TRUE.equals(profile.getEmailVerified()) && email == null)) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_PROFILE_INVALID);
        }
        return new ExternalIdentityProfile(
                externalSubject,
                displayName,
                email,
                Boolean.TRUE.equals(profile.getEmailVerified()),
                avatarUri);
    }

    /** @return 指定作用域中当前允许认证的 Provider */
    private @NotNull IdentityProvider requireActiveProvider(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId) {
        IdentityProvider provider = identityProviderRepository.findProvider(scope, providerId);
        if (provider == null) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_NOT_FOUND);
        }
        if (provider.getStatus() != IdentityProviderStatus.ACTIVE) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
        }
        return provider;
    }

    /** @return 作用域和状态均可用于创建 Session 的 ExternalIdentity 验证结果 */
    private @NotNull ExternalIdentityVerification verification(
            @Nullable IdentityProvider provider, @NotNull ExternalIdentityLink link) {
        IdentityProviderScope expectedScope = provider == null ? link.getProviderScope() : provider.getScope();
        if (!expectedScope.equals(link.getProviderScope())
                || link.getProviderStatus() != IdentityProviderStatus.ACTIVE
                || link.getSubject().getStatus() != SubjectStatus.ACTIVE
                || link.getAccount().getStatus() != AccountStatus.ACTIVE
                || link.getAccount().getScopeType() != expectedScope.getScopeType()
                || !Objects.equals(link.getAccount().getScopeId(), expectedScope.getScopeId())
                || !link.getSubject().getId().equals(link.getAccount().getSubjectId())) {
            throw new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE);
        }
        return new ExternalIdentityVerification(
                new Subject(link.getSubject().getId(), link.getSubject().getDisplayName()),
                new Account(
                        link.getAccount().getId(),
                        link.getAccount().getSubjectId(),
                        link.getAccount().getScopeType(),
                        link.getAccount().getScopeId(),
                        Boolean.TRUE.equals(link.getAccount().getSecondFactorRequired())),
                link.getIdentity().getIdentityId(),
                link.getIdentity().getVersion());
    }
}
