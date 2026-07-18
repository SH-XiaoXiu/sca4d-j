package com.sca4d.controllers.mg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.controllers.mg.platform.PlatformTenantAccountController;
import com.sca4d.controllers.mg.tenant.TenantAccountController;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountAccessPolicy;
import com.sca4d.iam.account.AccountSecondFactorChange;
import com.sca4d.iam.account.AccountSecurityService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.request.AccountPasswordCredentialBindRequest;
import com.sca4d.iam.account.request.AccountPasswordCredentialReplaceRequest;
import com.sca4d.iam.account.request.AccountSecondFactorRequest;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;

/** 验证平台代管和当前租户 Account 高危安全资源的统一约束。 */
class TenantAccountControllerTest {

    @Test
    void shouldExposeTenantAccountHighRiskRoutesWithIfMatchAndStepUp() throws Exception {
        for (Class<?> controller : List.of(PlatformTenantAccountController.class, TenantAccountController.class)) {
            RequestMapping root = controller.getAnnotation(RequestMapping.class);
            Method mfa = controller.getDeclaredMethod(
                    "changeSecondFactorRequirement",
                    Long.class,
                    Long.class,
                    AccountSecondFactorRequest.class,
                    String.class,
                    String.class,
                    Principal.class,
                    Subject.class,
                    Account.class,
                    HttpServletRequest.class);
            Method bindPassword = controller.getDeclaredMethod(
                    "bindPassword",
                    Long.class,
                    Long.class,
                    AccountPasswordCredentialBindRequest.class,
                    String.class,
                    String.class,
                    Principal.class,
                    Subject.class,
                    Account.class,
                    HttpServletRequest.class);
            Method recoveryCodes = controller.getDeclaredMethod(
                    "reissueRecoveryCodes",
                    Long.class,
                    Long.class,
                    String.class,
                    String.class,
                    Principal.class,
                    Subject.class,
                    Account.class,
                    HttpServletRequest.class);
            Method replacePassword = controller.getDeclaredMethod(
                    "replacePassword",
                    Long.class,
                    Long.class,
                    Long.class,
                    AccountPasswordCredentialReplaceRequest.class,
                    String.class,
                    String.class,
                    Principal.class,
                    Subject.class,
                    Account.class,
                    HttpServletRequest.class);
            Method revokeSession = controller.getDeclaredMethod(
                    "endSession",
                    Long.class,
                    Long.class,
                    String.class,
                    String.class,
                    String.class,
                    Principal.class,
                    Subject.class,
                    Account.class,
                    HttpServletRequest.class);

            assertThat(root.value()).containsExactly(controller == PlatformTenantAccountController.class
                    ? "/mg/platform/tenants/{tenantId}/accounts"
                    : "/mg/tenant/tenants/{tenantId}/accounts");
            assertThat(mfa.getAnnotation(PutMapping.class).value())
                    .containsExactly("/{accountId}/second-factor/requirement");
            assertThat(bindPassword.getAnnotation(PostMapping.class).value())
                    .containsExactly("/{accountId}/credentials/password");
            assertThat(recoveryCodes.getAnnotation(PostMapping.class).value())
                    .containsExactly("/{accountId}/recovery-codes");
            assertThat(replacePassword.getAnnotation(PutMapping.class).value())
                    .containsExactly("/{accountId}/credentials/{credentialId}/password");
            assertThat(revokeSession.getAnnotation(DeleteMapping.class).value())
                    .containsExactly("/{accountId}/sessions/{sessionId}");
            RequestHeader mfaIfMatch = (RequestHeader) mfa.getParameterAnnotations()[3][0];
            RequestHeader mfaStepUp = (RequestHeader) mfa.getParameterAnnotations()[4][0];
            RequestHeader revokeIfMatch = (RequestHeader) revokeSession.getParameterAnnotations()[3][0];
            RequestHeader revokeStepUp = (RequestHeader) revokeSession.getParameterAnnotations()[4][0];
            RequestHeader recoveryIfMatch = (RequestHeader) recoveryCodes.getParameterAnnotations()[2][0];
            RequestHeader recoveryStepUp = (RequestHeader) recoveryCodes.getParameterAnnotations()[3][0];
            assertThat(mfaIfMatch.value()).isEqualTo(HttpHeaders.IF_MATCH);
            assertThat(mfaStepUp.value()).isEqualTo(AuthenticationHeaders.AUTHENTICATION_ID);
            assertThat(mfaStepUp.required()).isTrue();
            assertThat(revokeIfMatch.value()).isEqualTo(HttpHeaders.IF_MATCH);
            assertThat(revokeStepUp.value()).isEqualTo(AuthenticationHeaders.AUTHENTICATION_ID);
            assertThat(revokeStepUp.required()).isTrue();
            assertThat(recoveryIfMatch.value()).isEqualTo(HttpHeaders.IF_MATCH);
            assertThat(recoveryStepUp.value()).isEqualTo(AuthenticationHeaders.AUTHENTICATION_ID);
            assertThat(recoveryStepUp.required()).isTrue();
        }
    }

    @Test
    void shouldUseTenantAccountPolicyAndTargetAccountForPlatformMfaChange() {
        AccountService accounts = mock(AccountService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AccountAccessPolicy policy = mock(AccountAccessPolicy.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IamAccountEntity target = tenantAccount();
        IamAccountEntity changed = tenantAccount().setSecondFactorRequired(true).setVersion(5L);
        AccountSecondFactorRequest body = new AccountSecondFactorRequest();
        body.setRequired(true);
        body.setVersion(4L);
        Principal principal = Principal.of(9L, PrincipalType.PLATFORM, 8L, null);
        Subject subject = new Subject(8L, "平台操作员");
        Account authenticatedAccount = new Account(7L, 8L, AccountScopeType.GLOBAL, null, false);
        when(accounts.require(80L)).thenReturn(target);
        when(browser.require(request)).thenReturn("browser-hash");
        when(security.changeManagedSecondFactorRequirement(
                subject, authenticatedAccount, 80L, true, 4L, "step-up-1", "browser-hash"))
                .thenReturn(new AccountSecondFactorChange(
                        changed,
                        new RecoveryCodeSummary(1L, 80L, OffsetDateTime.parse("2026-07-18T10:00:00+08:00"), 10, 0L),
                        List.of("AAAAA-BBBBB-CCCCC-DDDDD")));
        PlatformTenantAccountController controller = new PlatformTenantAccountController(
                accounts, security, policy, browser);

        var response = controller.changeSecondFactorRequirement(
                20L, 80L, body, "\"4\"", "step-up-1", principal, subject, authenticatedAccount, request);

        assertThat(response.getData().isRequired()).isTrue();
        verify(policy).requireTenantAccount(principal, 20L, target);
        verify(security).changeManagedSecondFactorRequirement(
                subject, authenticatedAccount, 80L, true, 4L, "step-up-1", "browser-hash");
    }

    @Test
    void shouldUseTenantAccountPolicyAndTargetAccountForTenantSessionRevocation() {
        AccountService accounts = mock(AccountService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AccountAccessPolicy policy = mock(AccountAccessPolicy.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IamAccountEntity target = tenantAccount();
        Principal principal = Principal.of(9L, PrincipalType.TENANT, 8L, 20L);
        Subject subject = new Subject(8L, "租户操作员");
        Account authenticatedAccount = new Account(7L, 8L, AccountScopeType.TENANT, 20L, false);
        when(accounts.require(80L)).thenReturn(target);
        when(browser.require(request)).thenReturn("browser-hash");
        TenantAccountController controller = new TenantAccountController(accounts, security, policy, browser);

        var response = controller.endSession(
                20L, 80L, "session-1", "\"4\"", "step-up-1", principal, subject, authenticatedAccount, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(policy).requireTenantAccount(principal, 20L, target);
        verify(security).endManagedSession(
                subject, authenticatedAccount, 80L, "session-1", "step-up-1", "browser-hash");
    }

    @Test
    void shouldRejectTenantAccountPolicyViolationBeforeSecurityOperation() {
        AccountService accounts = mock(AccountService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AccountAccessPolicy policy = mock(AccountAccessPolicy.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        IamAccountEntity target = tenantAccount();
        Principal principal = Principal.of(9L, PrincipalType.TENANT, 8L, 20L);
        when(accounts.require(80L)).thenReturn(target);
        org.mockito.Mockito.doThrow(new BizException(IamErrorCode.ACCOUNT_NOT_FOUND))
                .when(policy)
                .requireTenantAccount(principal, 21L, target);
        TenantAccountController controller = new TenantAccountController(accounts, security, policy, browser);
        AccountSecondFactorRequest body = new AccountSecondFactorRequest();
        body.setRequired(true);
        body.setVersion(4L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.changeSecondFactorRequirement(
                21L,
                80L,
                body,
                "\"4\"",
                "step-up-1",
                principal,
                new Subject(8L, "租户操作员"),
                new Account(7L, 8L, AccountScopeType.TENANT, 20L, false),
                mock(HttpServletRequest.class)))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(IamErrorCode.ACCOUNT_NOT_FOUND));
        verifyNoInteractions(security);
    }

    private static IamAccountEntity tenantAccount() {
        return new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.TENANT)
                .setScopeId(20L)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false)
                .setVersion(4L);
    }
}
