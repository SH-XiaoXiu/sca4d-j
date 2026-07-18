package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.authentication.passkey.request.PasskeyProofRequest;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.iam.passkey.request.PasskeyCredentialCreateRequest;
import com.sca4d.iam.passkey.request.PasskeyRegistrationCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/** 验证 Passkey ceremony 和 Credential 使用独立 REST 资源及共享限流。 */
class PasskeyControllerTest {

    @Test
    void shouldDeclareAuthenticationCeremonyResources() throws Exception {
        var assertion = PasskeyAuthenticationController.class.getDeclaredMethod(
                "createAssertion", String.class, HttpServletRequest.class);
        var proof = PasskeyAuthenticationController.class.getDeclaredMethod(
                "passkeyProof",
                String.class,
                PasskeyProofRequest.class,
                HttpServletRequest.class);

        assertThat(PasskeyAuthenticationController.class
                .getAnnotation(RequestMapping.class)
                .value())
                .containsExactly("/authentications/{authenticationId}");
        assertThat(assertion.getAnnotation(PostMapping.class).value()).containsExactly("/passkey-assertions");
        assertThat(proof.getAnnotation(PostMapping.class).value()).containsExactly("/passkey-proofs");
        assertPasskeyLimit(assertion.getAnnotation(RateLimit.class), "#authenticationId");
        assertPasskeyLimit(proof.getAnnotation(RateLimit.class), "#body.assertionId");
    }

    @Test
    void shouldDeclareRegistrationAndCredentialResources() throws Exception {
        var registration = PasskeyCredentialController.class.getDeclaredMethod(
                "createRegistration",
                Long.class,
                String.class,
                PasskeyRegistrationCreateRequest.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        var activate = PasskeyCredentialController.class.getDeclaredMethod(
                "activate",
                Long.class,
                PasskeyCredentialCreateRequest.class,
                Subject.class,
                HttpServletRequest.class);
        var delete = PasskeyCredentialController.class.getDeclaredMethod(
                "delete",
                Long.class,
                Long.class,
                String.class,
                String.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);

        assertThat(PasskeyCredentialController.class
                .getAnnotation(RequestMapping.class)
                .value())
                .containsExactly("/me/accounts/{accountId}/passkey");
        assertThat(PasskeyCredentialController.class.getAnnotation(AuthPrincipal.class).principalRequired()).isTrue();
        assertThat(registration.getAnnotation(PostMapping.class).value()).containsExactly("/registrations");
        assertThat(activate.getAnnotation(PostMapping.class).value()).containsExactly("/credentials");
        assertThat(delete.getAnnotation(DeleteMapping.class).value())
                .containsExactly("/credentials/{credentialId}");
        assertThat(delete.getParameters()[2].getAnnotation(RequestHeader.class).value())
                .isEqualTo(HttpHeaders.IF_MATCH);
        assertPasskeyLimit(activate.getAnnotation(RateLimit.class), "#body.registrationId");
        assertThat(PasskeyCredentialCreateRequest.class.getDeclaredField("credential")
                .getAnnotation(Schema.class).requiredMode())
                .isEqualTo(Schema.RequiredMode.REQUIRED);
        assertThat(PasskeyProofRequest.class.getDeclaredField("credential")
                .getAnnotation(Schema.class).requiredMode())
                .isEqualTo(Schema.RequiredMode.REQUIRED);
    }

    /** 检查客户端响应同时按来源地址和 ceremony UUID 限制提交次数。 */
    private void assertPasskeyLimit(RateLimit rateLimit, String key) {
        assertThat(rateLimit.types()).containsExactly(RateLimitType.IP, RateLimitType.CUSTOM);
        assertThat(rateLimit.key()).isEqualTo(key);
        assertThat(rateLimit.capacity()).isEqualTo(10);
        assertThat(rateLimit.refillTokens()).isEqualTo(10);
        assertThat(rateLimit.timeWindowSeconds()).isEqualTo(300);
    }
}
