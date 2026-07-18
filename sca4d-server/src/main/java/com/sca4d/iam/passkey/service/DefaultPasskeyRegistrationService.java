package com.sca4d.iam.passkey.service;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorAvailability;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.passkey.PasskeyCredential;
import com.sca4d.iam.passkey.PasskeyCredentialChange;
import com.sca4d.iam.passkey.PasskeyCredentialScheme;
import com.sca4d.iam.passkey.PasskeyRegistration;
import com.sca4d.iam.passkey.PasskeyRegistrationService;
import com.sca4d.iam.passkey.PasskeyRegistrationStart;
import com.sca4d.iam.passkey.PasskeyRegistrationState;
import com.sca4d.iam.passkey.repository.PasskeyRegistrationRepository;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** 使用 STEP_UP、Yubico 验证和 Redis CAS 注册或删除当前 Account 的 Passkey。 */
@Service
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DefaultPasskeyRegistrationService implements PasskeyRegistrationService {

    private final AccountService accounts;
    private final CredentialService credentials;
    private final PasskeyCredentialScheme passkeys;
    private final PasskeyRegistrationRepository registrations;
    private final PasskeyRepository passkeyRepository;
    private final AccountSecondFactorAvailability secondFactors;
    private final AuthenticationProcessService authentications;
    private final Authentication authentication;
    private final Sca4djAuthenticationProperties properties;

    @Override
    public @NotNull PasskeyRegistrationStart create(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String displayName) {
        requireOwnedAccount(subject.getSubjectId(), targetAccountId);
        // registration 含浏览器可用的 challenge，只有当前 Session Account 完成 STEP_UP 后才能创建。
        authentications.authorizeStepUp(
                authenticationId, browserHash, subject, authenticatedAccount);
        byte[] userHandle = passkeys.registrationUserHandle(targetAccountId);
        var request = passkeys.registrationOptions(
                userHandle,
                subject.getDisplayName(),
                properties.getPasskey().getCeremonyTtl().toMillis());
        String requestJson = passkeys.registrationRequestJson(request);
        Instant now = Instant.now();
        PasskeyRegistration registration = new PasskeyRegistration()
                .setRegistrationId(UUID.randomUUID().toString())
                .setState(PasskeyRegistrationState.PENDING)
                .setVersion(0L)
                .setSubjectId(subject.getSubjectId())
                .setAccountId(targetAccountId)
                .setBrowserHash(browserHash)
                .setRequestJson(requestJson)
                .setDisplayName(displayName)
                .setCreatedAt(now)
                .setExpiresAt(now.plus(properties.getPasskey().getCeremonyTtl()));
        registrations.create(registration, properties.getPasskey().getCeremonyTtl());
        log.info(
                "Passkey registration 已创建 | subjectId={} authenticatedAccountId={} targetAccountId={} registrationId={} expiresAt={}",
                subject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                registration.getRegistrationId(),
                registration.getExpiresAt());
        return new PasskeyRegistrationStart(
                registration, passkeys.browserRegistrationOptions(requestJson));
    }

    @Override
    @DistributedLock(key = "'subject:' + #subjectId")
    @Transactional
    public @NotNull PasskeyCredentialChange activate(
            @NotNull Long subjectId,
            @NotNull Long accountId,
            @NotNull String registrationId,
            @NotNull String responseJson,
            @NotNull String browserHash) {
        requireOwnedAccount(subjectId, accountId);
        PasskeyRegistration registration = registrations.find(registrationId);
        Instant now = Instant.now();
        if (registration == null
                || registration.getState() != PasskeyRegistrationState.PENDING
                || registration.getExpiresAt() == null
                || !registration.getExpiresAt().isAfter(now)
                || !subjectId.equals(registration.getSubjectId())
                || !accountId.equals(registration.getAccountId())
                || !browserHash.equals(registration.getBrowserHash())) {
            throw new BizException(IamErrorCode.PASSKEY_REGISTRATION_UNAVAILABLE);
        }
        // 客户端响应无论验证成功与否都只能提交一次；失败后必须重新取得 challenge。
        Long expectedVersion = registration.getVersion();
        registration.consume();
        if (!registrations.consume(registration, expectedVersion)) {
            throw new BizException(IamErrorCode.PASSKEY_REGISTRATION_CHANGED);
        }
        PasskeyCredential credential = passkeys.registrationCredential(
                registration.getRequestJson(), responseJson, registration.getDisplayName());
        // Credential 写入与旧 Session 失效使用同一事务；Session 清理失败时数据库新增一起回滚。
        requireOwnedAccount(subjectId, accountId);
        Long credentialId = credentials.bind(accountId, credential);
        authentication.endSessions(accountId);
        log.info(
                "Passkey Credential 已写入事务，现有登录会话已结束 | subjectId={} accountId={} credentialId={}",
                subjectId,
                accountId,
                credentialId);
        return new PasskeyCredentialChange(credentialId, 0L);
    }

    @Override
    @DistributedLock(key = "'subject:' + #subject.subjectId")
    @Transactional
    public void delete(
            @NotNull Subject subject,
            @NotNull Account authenticatedAccount,
            @NotNull Long targetAccountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull String authenticationId,
            @NotNull String browserHash) {
        IamAccountEntity targetAccount = requireOwnedAccount(
                subject.getSubjectId(), targetAccountId);
        if (Boolean.TRUE.equals(targetAccount.getSecondFactorRequired())
                && !secondFactors.availableAfterRemovingCredential(targetAccount, credentialId)) {
            throw new BizException(IamErrorCode.PASSKEY_REMOVAL_WOULD_LOCK_ACCOUNT);
        }
        // STEP_UP 消费和类型限定删除在同一 Subject 锁内完成；失败后 Proof 不恢复。
        authentications.authorizeStepUp(
                authenticationId, browserHash, subject, authenticatedAccount);
        passkeyRepository.delete(credentialId, targetAccountId, version);
        authentication.endSessions(targetAccountId);
        log.info(
                "Passkey Credential 已删除，现有登录会话已结束 | subjectId={} authenticatedAccountId={} targetAccountId={} credentialId={}",
                subject.getSubjectId(),
                authenticatedAccount.getAccountId(),
                targetAccountId,
                credentialId);
    }

    /**
     * 确认 Account 属于当前 Subject，避免泄漏其他 Subject 的 Passkey 资源。
     *
     * @return 当前 Subject 名下未删除 Account
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
