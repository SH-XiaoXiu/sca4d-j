package com.sca4d.iam.passkey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialScheme;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.error.KnownCredentialRejectedException;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import com.sca4d.iam.passkey.repository.PasskeyRow;
import com.sca4d.iam.subject.SubjectStatus;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** 使用 Yubico WebAuthn Server 注册 Passkey、验证 assertion 并原子更新认证器计数。 */
@Component
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
@Slf4j
public class PasskeyCredentialScheme implements CredentialScheme<PasskeyCredential, PasskeyProof> {

    private static final SecureRandom USER_HANDLE_RANDOM = new SecureRandom();

    private final PasskeyRepository repository;
    private final RelyingParty relyingParty;
    private final IdGenerator idGenerator;

    @Override
    public @NotNull CredentialType credentialType() {
        return CredentialType.PASSKEY;
    }

    @Override
    public @NotNull Class<PasskeyCredential> credentialClass() {
        return PasskeyCredential.class;
    }

    @Override
    public @NotNull Class<PasskeyProof> proofClass() {
        return PasskeyProof.class;
    }

    @Override
    public @NotNull Long bind(
            @NotNull Long accountId, @NotNull PasskeyCredential credential) {
        Long credentialId = idGenerator.nextId();
        repository.save(
                credentialId,
                accountId,
                credential.getUserHandle(),
                credential.getPasskeyCredentialId(),
                credential.getPublicKeyCose(),
                credential.getSignatureCount(),
                credential.getAaguid(),
                credential.isBackupEligible(),
                credential.isBackupState(),
                credential.getDisplayName());
        return credentialId;
    }

    @Override
    public void replace(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull PasskeyCredential credential) {
        throw new BizException(IamErrorCode.CREDENTIAL_TYPE_UNSUPPORTED);
    }

    @Override
    public @NotNull CredentialVerification verify(
            @NotNull Long accountId, @NotNull PasskeyProof proof) {
        CredentialVerification verification = verify(null, null, proof);
        if (!accountId.equals(verification.getAccount().getAccountId())) {
            throw new UnauthenticatedException(AuthenticationErrorCode.PASSKEY_ASSERTION_INVALID);
        }
        return verification;
    }

    /**
     * 创建要求 discoverable credential 和用户验证的 registration options。
     *
     * @param userHandle Account 对应的随机不透明标识
     * @param displayName 浏览器和认证器显示的 Subject 名称
     * @param timeoutMillis 浏览器 ceremony 超时毫秒数
     */
    public @NotNull PublicKeyCredentialCreationOptions registrationOptions(
            byte @NotNull [] userHandle,
            @NotNull String displayName,
            long timeoutMillis) {
        String username = Base64.getUrlEncoder().withoutPadding().encodeToString(userHandle);
        UserIdentity user = UserIdentity.builder()
                .name(username)
                .displayName(displayName)
                .id(new ByteArray(userHandle))
                .build();
        return relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(user)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .timeout(timeoutMillis)
                .build());
    }

    /**
     * 校验 registration challenge、RP、Origin、UV 和 attestation 后生成可持久化公钥。
     *
     * @throws BizException 响应 JSON 无效或任一 WebAuthn 检查失败时抛出
     */
    public @NotNull PasskeyCredential registrationCredential(
            @NotNull String requestJson,
            @NotNull String responseJson,
            @NotNull String displayName) {
        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
            RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(request)
                    .response(PublicKeyCredential.parseRegistrationResponseJson(responseJson))
                    .build());
            if (!result.isUserVerified()) {
                throw new BizException(AuthenticationErrorCode.PASSKEY_ASSERTION_INVALID);
            }
            return new PasskeyCredential(
                    request.getUser().getId().getBytes(),
                    result.getKeyId().getId().getBytes(),
                    result.getPublicKeyCose().getBytes(),
                    result.getSignatureCount(),
                    result.getAaguid().getBytes(),
                    result.isBackupEligible(),
                    result.isBackedUp(),
                    displayName);
        } catch (IOException | RegistrationFailedException | IllegalArgumentException exception) {
            throw new BizException(AuthenticationErrorCode.PASSKEY_ASSERTION_INVALID, exception);
        }
    }

    /**
     * 创建要求用户验证的 assertion request。
     *
     * @param userHandle Account-bound STEP_UP/第二因子使用的 user handle；无账号 LOGIN 时为空
     * @param timeoutMillis 浏览器 ceremony 超时毫秒数
     */
    public @NotNull AssertionRequest assertionRequest(
            byte @Nullable [] userHandle, long timeoutMillis) {
        StartAssertionOptions.StartAssertionOptionsBuilder options = StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(timeoutMillis);
        if (userHandle != null) {
            options.userHandle(new ByteArray(userHandle));
        }
        return relyingParty.startAssertion(options.build());
    }

    /**
     * 校验 assertion 并用数据库当前 signCount、备份资格和 Credential 版本原子保存结果。
     *
     * @param scopeType LOGIN Flow 请求的 Account scope；按 Account 认证时为空
     * @param scopeId TENANT scope 主键；GLOBAL 或按 Account 认证时为空
     */
    public @NotNull CredentialVerification verify(
            @Nullable AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull PasskeyProof proof) {
        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(AssertionRequest.fromJson(proof.requestJson()))
                    .response(PublicKeyCredential.parseAssertionResponseJson(proof.responseJson()))
                    .build());
        } catch (IOException | AssertionFailedException | IllegalArgumentException exception) {
            // 只记录固定失败分类，不能把 Yubico 异常消息中的 credential ID、user handle 或 COSE 公钥写入日志。
            log.debug("Passkey assertion 协议校验失败 | failure={}", assertionFailure(exception));
            throw new UnauthenticatedException(AuthenticationErrorCode.PASSKEY_ASSERTION_INVALID);
        }
        PasskeyRow row = repository.find(
                result.getCredentialId().getBytes(), result.getUserHandle().getBytes());
        String rejected = null;
        if (!result.isSuccess()) {
            rejected = "result";
        } else if (!result.isUserVerified()) {
            rejected = "user-verification";
        } else if (row == null) {
            rejected = "credential";
        } else if (row.getCredentialStatus() != CredentialStatus.ACTIVE) {
            rejected = "credential-status";
        } else if (row.getAccountStatus() != AccountStatus.ACTIVE) {
            rejected = "account-status";
        } else if (row.getSubjectStatus() != SubjectStatus.ACTIVE) {
            rejected = "subject-status";
        } else if (scopeType != null
                && (row.getScopeType() != scopeType
                        || !Objects.equals(row.getScopeId(), scopeId))) {
            rejected = "account-scope";
        } else if (!Boolean.valueOf(result.isBackupEligible()).equals(row.getBackupEligible())) {
            rejected = "backup-eligibility";
        }
        if (rejected != null) {
            log.debug("Passkey assertion 当前账号复核失败 | failure={}", rejected);
            throw new UnauthenticatedException(AuthenticationErrorCode.PASSKEY_ASSERTION_INVALID);
        }
        Long version = repository.advanceAssertion(
                row.getCredentialId(),
                row.getAccountId(),
                row.getSignatureCount(),
                result.getSignatureCount(),
                result.isBackupEligible(),
                result.isBackedUp(),
                row.getCredentialVersion());
        if (version == null) {
            log.debug("Passkey assertion 原子更新失败 | failure=credential-version-or-counter");
            throw new KnownCredentialRejectedException(
                    row.getCredentialId(),
                    row.getSubjectId(),
                    AuthenticationErrorCode.PASSKEY_ASSERTION_INVALID);
        }
        return new CredentialVerification(
                new Subject(row.getSubjectId(), row.getSubjectDisplayName()),
                new Account(
                        row.getAccountId(),
                        row.getSubjectId(),
                        row.getScopeType(),
                        row.getScopeId(),
                        Boolean.TRUE.equals(row.getSecondFactorRequired())),
                row.getCredentialId(),
                version);
    }

    /**
     * 将 Yubico assertion 异常归入不包含认证材料的固定分类。
     *
     * @return 供 DEBUG 日志定位校验阶段的分类，不包含异常原文
     */
    private @NotNull String assertionFailure(@NotNull Exception exception) {
        Throwable failure = exception instanceof AssertionFailedException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        String message = failure.getMessage();
        if (message == null) {
            return "malformed-response";
        }
        if (message.startsWith("Incorrect origin"))
            return "origin";
        if (message.equals("Incorrect challenge."))
            return "challenge";
        if (message.equals("Wrong RP ID hash."))
            return "rp-id";
        if (message.equals("User Verification is required."))
            return "user-verification";
        if (message.equals("User Presence is required."))
            return "user-presence";
        if (message.equals("Invalid assertion signature."))
            return "signature";
        if (message.startsWith("Unknown credential") || message.startsWith("Unrequested credential ID")) {
            return "credential";
        }
        if (message.contains("user handle") || message.contains("User handle"))
            return "user-handle";
        if (failure.getClass().getSimpleName().equals("InvalidSignatureCountException")) {
            return "signature-count";
        }
        return "malformed-response";
    }

    /**
     * 读取 Account 已保存的 WebAuthn user handle；首次注册时只生成候选值。
     *
     * <p>候选值随 registration request 保存到 Redis，Credential 激活时才与公钥在同一数据库事务中写入。
     *
     * @return 已保存或新生成的 32 字节不透明标识
     */
    public byte @NotNull [] registrationUserHandle(@NotNull Long accountId) {
        byte[] existing = repository.findUserHandle(accountId);
        if (existing != null) {
            return existing;
        }
        byte[] candidate = new byte[32];
        USER_HANDLE_RANDOM.nextBytes(candidate);
        return candidate;
    }

    /** @return Account 已保存的 user handle；尚未注册 Passkey 时为 {@code null} */
    public byte @Nullable [] existingUserHandle(@NotNull Long accountId) {
        return repository.findUserHandle(accountId);
    }

    /** @return Yubico request 的持久化 JSON；序列化失败时作为服务内部错误 */
    public @NotNull String registrationRequestJson(
            @NotNull PublicKeyCredentialCreationOptions request) {
        try {
            return request.toJson();
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey registration request 序列化失败", exception);
        }
    }

    /** @return 浏览器 {@code navigator.credentials.create()} 使用的 JSON */
    public @NotNull String browserRegistrationOptions(@NotNull String requestJson) {
        try {
            return PublicKeyCredentialCreationOptions.fromJson(requestJson).toCredentialsCreateJson();
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey registration request 反序列化失败", exception);
        }
    }

    /** @return Yubico assertion request 的持久化 JSON；序列化失败时作为服务内部错误 */
    public @NotNull String assertionRequestJson(@NotNull AssertionRequest request) {
        try {
            return request.toJson();
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey assertion request 序列化失败", exception);
        }
    }

    /** @return 浏览器 {@code navigator.credentials.get()} 使用的 JSON */
    public @NotNull String browserAssertionOptions(@NotNull String requestJson) {
        try {
            return AssertionRequest.fromJson(requestJson).toCredentialsGetJson();
        } catch (JsonProcessingException exception) {
            throw new InternalServiceException("Passkey assertion request 反序列化失败", exception);
        }
    }
}
