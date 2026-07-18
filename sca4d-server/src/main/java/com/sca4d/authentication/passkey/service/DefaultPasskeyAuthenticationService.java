package com.sca4d.authentication.passkey.service;

import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.authentication.passkey.PasskeyAssertion;
import com.sca4d.authentication.passkey.PasskeyAssertionStart;
import com.sca4d.authentication.passkey.PasskeyAssertionState;
import com.sca4d.authentication.passkey.PasskeyAuthenticationService;
import com.sca4d.authentication.passkey.repository.PasskeyAssertionRepository;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.iam.passkey.PasskeyCredentialScheme;
import com.sca4d.iam.passkey.PasskeyProof;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** 创建一次性 WebAuthn assertion，并把浏览器响应提交给 PASSKEY 认证方式。 */
@Service
@ConditionalOnProperty(prefix = "sca4dj.authentication.passkey", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DefaultPasskeyAuthenticationService implements PasskeyAuthenticationService {

    private final AuthenticationProcessService authentications;
    private final PasskeyCredentialScheme passkeys;
    private final PasskeyAssertionRepository assertions;
    private final Sca4djAuthenticationProperties properties;

    @Override
    public @NotNull PasskeyAssertionStart createAssertion(
            @NotNull String authenticationId, @NotNull String browserHash) {
        AuthenticationProcess authentication = authentications.requireProof(
                authenticationId, browserHash, AuthMethodType.PASSKEY);
        boolean accountProof = authentication.getAccountId() != null;
        byte[] userHandle = accountProof
                ? passkeys.existingUserHandle(authentication.getAccountId())
                : null;
        if (accountProof && userHandle == null) {
            throw new BizException(AuthenticationErrorCode.SECOND_FACTOR_UNAVAILABLE);
        }
        var request = passkeys.assertionRequest(
                userHandle, properties.getPasskey().getCeremonyTtl().toMillis());
        String requestJson = passkeys.assertionRequestJson(request);
        Instant now = Instant.now();
        PasskeyAssertion assertion = new PasskeyAssertion()
                .setAssertionId(UUID.randomUUID().toString())
                .setState(PasskeyAssertionState.PENDING)
                .setVersion(0L)
                .setAuthenticationId(authenticationId)
                .setBrowserHash(browserHash)
                .setRequestJson(requestJson)
                .setCreatedAt(now)
                .setExpiresAt(now.plus(properties.getPasskey().getCeremonyTtl()));
        assertions.create(assertion, properties.getPasskey().getCeremonyTtl());
        log.info(
                "Passkey 请求已创建 | authenticationId={} assertionId={} activityId={} accountBound={} expiresAt={}",
                authenticationId,
                assertion.getAssertionId(),
                authentication.getActivityId(),
                accountProof,
                assertion.getExpiresAt());
        return new PasskeyAssertionStart(
                assertion, passkeys.browserAssertionOptions(requestJson));
    }

    @Override
    public @NotNull AuthenticationProcess proof(
            @NotNull String authenticationId,
            @NotNull String assertionId,
            @NotNull String browserHash,
            @NotNull String responseJson) {
        PasskeyAssertion assertion = assertions.find(assertionId);
        Instant now = Instant.now();
        if (assertion == null
                || assertion.getState() != PasskeyAssertionState.PENDING
                || assertion.getExpiresAt() == null
                || !assertion.getExpiresAt().isAfter(now)
                || !authenticationId.equals(assertion.getAuthenticationId())
                || !browserHash.equals(assertion.getBrowserHash())) {
            throw new BizException(AuthenticationErrorCode.PASSKEY_ASSERTION_UNAVAILABLE);
        }
        // assertion 响应无论验证成功与否都只能提交一次，避免同一 challenge 反复试探或并发重放。
        Long expectedVersion = assertion.getVersion();
        assertion.consume();
        if (!assertions.consume(assertion, expectedVersion)) {
            throw new BizException(AuthenticationErrorCode.PASSKEY_ASSERTION_CHANGED);
        }
        AuthenticationProcess authentication = authentications.proof(
                authenticationId,
                browserHash,
                AuthMethodType.PASSKEY,
                PasskeyProof.class,
                new PasskeyProof(assertion.getRequestJson(), responseJson));
        log.info(
                "Passkey 响应验证通过 | authenticationId={} assertionId={} activityId={} subjectId={} accountId={}",
                authenticationId,
                assertionId,
                authentication.getActivityId(),
                authentication.getSubjectId(),
                authentication.getAccountId());
        return authentication;
    }
}
