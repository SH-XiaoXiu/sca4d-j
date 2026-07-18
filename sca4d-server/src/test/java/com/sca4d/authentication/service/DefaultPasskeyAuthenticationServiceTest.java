package com.sca4d.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.authentication.passkey.PasskeyAssertion;
import com.sca4d.authentication.passkey.PasskeyAssertionState;
import com.sca4d.authentication.passkey.repository.PasskeyAssertionRepository;
import com.sca4d.authentication.passkey.service.DefaultPasskeyAuthenticationService;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.iam.passkey.PasskeyCredentialScheme;
import com.sca4d.iam.passkey.PasskeyProof;
import com.yubico.webauthn.AssertionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证 Passkey assertion 的认证任务、浏览器绑定和 Redis 单次消费顺序。 */
class DefaultPasskeyAuthenticationServiceTest {

    @Test
    void shouldCreateDiscoverableAssertionForAnonymousLogin() {
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        PasskeyCredentialScheme passkeys = mock(PasskeyCredentialScheme.class);
        PasskeyAssertionRepository assertions = mock(PasskeyAssertionRepository.class);
        AssertionRequest request = mock(AssertionRequest.class);
        when(flows.requireProof("flow-1", "browser-hash", AuthMethodType.PASSKEY)).thenReturn(new AuthenticationProcess()
                .setAuthenticationId("flow-1")
                .setPurpose(AuthenticationPurpose.LOGIN)
                .setActivityId("primary-proof"));
        when(passkeys.assertionRequest(null, 300_000L)).thenReturn(request);
        when(passkeys.assertionRequestJson(request)).thenReturn("request-json");
        when(passkeys.browserAssertionOptions("request-json")).thenReturn("{\"publicKey\":{}}");

        var result = new DefaultPasskeyAuthenticationService(
                flows, passkeys, assertions, new Sca4djAuthenticationProperties())
                .createAssertion("flow-1", "browser-hash");

        assertThat(result.getAssertion().getAuthenticationId()).isEqualTo("flow-1");
        assertThat(result.getAssertion().getState()).isEqualTo(PasskeyAssertionState.PENDING);
        assertThat(result.getBrowserOptionsJson()).isEqualTo("{\"publicKey\":{}}");
    }

    @Test
    void shouldConsumeAssertionBeforeSubmittingTypedProof() {
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        PasskeyAssertionRepository assertions = mock(PasskeyAssertionRepository.class);
        PasskeyAssertion assertion = new PasskeyAssertion()
                .setAssertionId("assertion-1")
                .setState(PasskeyAssertionState.PENDING)
                .setVersion(0L)
                .setAuthenticationId("flow-1")
                .setBrowserHash("browser-hash")
                .setRequestJson("request-json")
                .setExpiresAt(Instant.now().plusSeconds(60));
        AuthenticationProcess verified = new AuthenticationProcess()
                .setAuthenticationId("flow-1")
                .setActivityId("completion");
        when(assertions.find("assertion-1")).thenReturn(assertion);
        when(assertions.consume(assertion, 0L)).thenReturn(true);
        when(flows.proof(
                eq("flow-1"),
                eq("browser-hash"),
                eq(AuthMethodType.PASSKEY),
                eq(PasskeyProof.class),
                any(PasskeyProof.class)))
                .thenReturn(verified);

        AuthenticationProcess result = new DefaultPasskeyAuthenticationService(
                flows,
                mock(PasskeyCredentialScheme.class),
                assertions,
                new Sca4djAuthenticationProperties())
                .proof("flow-1", "assertion-1", "browser-hash", "response-json");

        assertThat(result.getActivityId()).isEqualTo("completion");
        var order = inOrder(assertions, flows);
        order.verify(assertions).consume(assertion, 0L);
        order.verify(flows).proof(
                eq("flow-1"),
                eq("browser-hash"),
                eq(AuthMethodType.PASSKEY),
                eq(PasskeyProof.class),
                any(PasskeyProof.class));
    }

    @Test
    void shouldRejectCompletedFlowBeforeCreatingAssertion() {
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        when(flows.requireProof("flow-1", "browser-hash", AuthMethodType.PASSKEY))
                .thenThrow(new BizException(AuthenticationErrorCode.AUTHENTICATION_ACTIVITY_INVALID));

        assertThatThrownBy(() -> new DefaultPasskeyAuthenticationService(
                flows,
                mock(PasskeyCredentialScheme.class),
                mock(PasskeyAssertionRepository.class),
                new Sca4djAuthenticationProperties())
                .createAssertion("flow-1", "browser-hash"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(AuthenticationErrorCode.AUTHENTICATION_ACTIVITY_INVALID);
    }
}
