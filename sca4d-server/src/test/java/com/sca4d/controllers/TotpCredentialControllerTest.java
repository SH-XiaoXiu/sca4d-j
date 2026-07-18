package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.concurrent.ratelimit.RateLimitType;
import com.sca4d.iam.totp.request.TotpCredentialCreateRequest;
import com.sca4d.iam.totp.request.TotpCredentialReplaceRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/** 验证 TOTP enrollment 和 Credential 使用 REST 资源路由及集群限流声明。 */
class TotpCredentialControllerTest {

    @Test
    void shouldDeclareEnrollmentAndCredentialResourceRoutes() throws Exception {
        PostMapping enrollment = TotpCredentialController.class
                .getDeclaredMethod(
                        "createEnrollment",
                        Long.class,
                        String.class,
                        Subject.class,
                        Account.class,
                        HttpServletRequest.class)
                .getAnnotation(PostMapping.class);
        GetMapping qrCode = TotpCredentialController.class
                .getDeclaredMethod(
                        "qrCode",
                        Long.class,
                        String.class,
                        Subject.class,
                        HttpServletRequest.class)
                .getAnnotation(GetMapping.class);
        var deleteMethod = TotpCredentialController.class
                .getDeclaredMethod(
                        "delete",
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        Subject.class,
                        Account.class,
                        HttpServletRequest.class);
        DeleteMapping delete = deleteMethod.getAnnotation(DeleteMapping.class);

        assertThat(TotpCredentialController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/me/accounts/{accountId}/totp");
        assertThat(TotpCredentialController.class.getAnnotation(AuthPrincipal.class).principalRequired()).isTrue();
        assertThat(enrollment.value()).containsExactly("/enrollments");
        assertThat(qrCode.value()).containsExactly("/enrollments/{enrollmentId}/qr-code");
        assertThat(delete.value()).containsExactly("/credentials/{credentialId}");
        assertThat(deleteMethod.getParameters()[2].getAnnotation(RequestHeader.class).value())
                .isEqualTo(HttpHeaders.IF_MATCH);
    }

    @Test
    void shouldRateLimitActivationAndReplacementBySourceAndEnrollment() throws Exception {
        var activate = TotpCredentialController.class.getDeclaredMethod(
                "activate",
                Long.class,
                TotpCredentialCreateRequest.class,
                Subject.class,
                HttpServletRequest.class);
        var replace = TotpCredentialController.class.getDeclaredMethod(
                "replace",
                Long.class,
                Long.class,
                TotpCredentialReplaceRequest.class,
                String.class,
                Subject.class,
                HttpServletRequest.class);
        RateLimit activateLimit = activate.getAnnotation(RateLimit.class);
        RateLimit replaceLimit = replace.getAnnotation(RateLimit.class);

        assertThat(activate.getAnnotation(PostMapping.class).value()).containsExactly("/credentials");
        assertThat(replace.getAnnotation(PutMapping.class).value())
                .containsExactly("/credentials/{credentialId}");
        assertThat(replace.getParameters()[3].getAnnotation(RequestHeader.class).value())
                .isEqualTo(HttpHeaders.IF_MATCH);
        assertEnrollmentLimit(activateLimit);
        assertEnrollmentLimit(replaceLimit);
    }

    /** 检查 enrollment 验证码同时按来源地址和 enrollment UUID 限制尝试次数。 */
    private void assertEnrollmentLimit(RateLimit rateLimit) {
        assertThat(rateLimit.types()).containsExactly(RateLimitType.IP, RateLimitType.CUSTOM);
        assertThat(rateLimit.key()).isEqualTo("#body.enrollmentId");
        assertThat(rateLimit.capacity()).isEqualTo(10);
        assertThat(rateLimit.refillTokens()).isEqualTo(10);
        assertThat(rateLimit.timeWindowSeconds()).isEqualTo(300);
    }
}
