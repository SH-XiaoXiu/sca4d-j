package com.sca4d.auth.passkey.ceremony;

import com.sca4d.auth.passkey.PasskeyErrorCode;
import com.sca4d.auth.passkey.PasskeyProof;
import com.sca4d.auth.passkey.credential.PasskeyCredential;
import com.sca4d.auth.passkey.credential.PasskeyCredentialRepository;
import com.sca4d.auth.passkey.exception.AuthPasskeyException;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/**
 * 开始 discoverable Passkey assertion，并完成一次性响应校验和签名计数器更新。
 */
public class PasskeyAssertionService {

    private static final Logger LOG = LoggerFactory.getLogger(PasskeyAssertionService.class);

    private static final int BACKED_UP_FLAG = 0x10;

    private final PasskeyCredentialRepository credentials;

    private final Clock clock;

    private final PasskeyCeremonyStore ceremonies;

    private final RelyingParty relyingParty;

    /**
     * 创建 Passkey assertion 服务。
     */
    public PasskeyAssertionService(
            @NotNull RelyingParty relyingParty,
            @NotNull PasskeyCeremonyStore ceremonies,
            @NotNull PasskeyCredentialRepository credentials,
            @NotNull Clock clock) {
        this.relyingParty = Objects.requireNonNull(relyingParty, "WebAuthn RelyingParty 不能为空");
        this.ceremonies = Objects.requireNonNull(ceremonies, "Passkey ceremony 存储不能为空");
        this.credentials = Objects.requireNonNull(credentials, "Passkey Credential Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 开始不暴露账号是否存在的 discoverable assertion，并强制用户验证。
     */
    public @NotNull PasskeyAssertionOptions start() {
        AssertionRequest request = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build());
        try {
            PasskeyCeremonyStore.StoredCeremony stored = ceremonies.storeAssertion(request.toJson());
            LOG.debug(
                    "Passkey 认证流程已创建 | ceremonyId={} expiresAt={}",
                    stored.id().value(),
                    stored.expiresAt());
            return new PasskeyAssertionOptions(
                    stored.id(),
                    request.toCredentialsGetJson(),
                    stored.expiresAt());
        } catch (IOException exception) {
            throw new AuthPasskeyException("Passkey assertion options 编码失败", exception);
        }
    }

    /**
     * 原子消费 assertion ceremony，验证响应并保存签名计数器。
     *
     * @param expectedAccount 认证引擎已经确定的账号；允许方法从 discoverable Credential 识别账号时为 {@code null}
     * @param proof           浏览器 assertion 响应
     * @return assertion 验证并更新后的 Credential，签名计数器已在当前事务中更新
     * @throws BusinessException ceremony 不存在或已消费时抛出 CEREMONY_UNAVAILABLE；响应无法验证、Credential 不存在、
     *                           与期望账号不一致或版本推进失败时抛出 ASSERTION_REJECTED
     */
    @Transactional
    public @NotNull PasskeyCredential finish(
            @Nullable Account expectedAccount,
            @NotNull PasskeyProof proof) {
        PasskeyProof checkedProof = Objects.requireNonNull(proof, "Passkey Proof 不能为空");

        // ceremony 在校验前原子消费，同一浏览器响应即使校验失败也不能再次提交。
        String requestJson = ceremonies.consumeAssertion(checkedProof.ceremonyId());
        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> response = PublicKeyCredential
                    .parseAssertionResponseJson(checkedProof.responseJson());

            // Yubico 同时校验 challenge、Origin、RP ID、签名、用户验证和签名计数器。
            AssertionResult result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(AssertionRequest.fromJson(requestJson))
                            .response(response)
                            .build());
            if (!result.isSuccess() || !result.isUserVerified() || !result.isSignatureCounterValid()) {
                throw new BusinessException(PasskeyErrorCode.ASSERTION_REJECTED);
            }
            // 结果返回本次实际校验的 Credential，模块再复核期望账号并读取乐观版本。
            PasskeyCredential credential = credentials.findByCredentialId(
                    result.getCredential().getCredentialId().getBytes());
            if (credential == null
                    || (expectedAccount != null && credential.accountId() != expectedAccount.accountId())) {
                throw new BusinessException(PasskeyErrorCode.ASSERTION_REJECTED);
            }

            // 通过读取版本和旧计数器原子推进 Credential，防止并发 assertion 覆盖新状态。
            boolean backedUp = (response.getResponse()
                    .getParsedAuthenticatorData()
                    .getFlags().value
                    & BACKED_UP_FLAG) != 0;
            PasskeyCredential updated = credentials.advanceSignatureCount(
                    credential,
                    result.getSignatureCount(),
                    backedUp,
                    clock.instant());
            if (updated == null) {
                throw new BusinessException(PasskeyErrorCode.ASSERTION_REJECTED);
            }
            LOG.debug(
                    "Passkey 认证结果已写入事务 | accountId={} credentialId={} signatureCount={}",
                    updated.accountId(),
                    updated.id(),
                    updated.signatureCount());
            return updated;
        } catch (AssertionFailedException | IOException exception) {
            throw new BusinessException(PasskeyErrorCode.ASSERTION_REJECTED, exception);
        }
    }
}
