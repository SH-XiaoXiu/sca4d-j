package com.sca4d.controllers.mg.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountAccessPolicy;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.AccountSecondFactorChange;
import com.sca4d.iam.account.AccountSecurityService;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.request.AccountPasswordCredentialReplaceRequest;
import com.sca4d.iam.account.request.AccountSecondFactorRequest;
import com.sca4d.iam.account.request.ManagedAccountProfileUpdateRequest;
import com.sca4d.iam.account.request.ManagedAccountStatusRequest;
import com.sca4d.iam.account.service.AccountLifecycleService;
import com.sca4d.iam.account.service.ManagedAccountProfileService;
import com.sca4d.iam.profile.service.DefaultProfileService;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;

/** 验证平台 Account 的 MFA 与 Session 管理边界。 */
class PlatformAccountControllerTest {

    @Test
    void shouldExposeAccountScopedMfaAndSessionRoutes() throws Exception {
        RequestMapping root = PlatformAccountController.class.getAnnotation(RequestMapping.class);
        GetMapping sessions = PlatformAccountController.class
                .getDeclaredMethod("sessions", Long.class, Principal.class)
                .getAnnotation(GetMapping.class);
        Method mfa = PlatformAccountController.class.getDeclaredMethod(
                "changeSecondFactorRequirement",
                Long.class,
                AccountSecondFactorRequest.class,
                String.class,
                String.class,
                Principal.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        PutMapping mfaMapping = mfa.getAnnotation(PutMapping.class);
        Method recoveryCodes = PlatformAccountController.class.getDeclaredMethod(
                "reissueRecoveryCodes",
                Long.class,
                String.class,
                String.class,
                Principal.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        Method revoke = PlatformAccountController.class.getDeclaredMethod(
                "endSession",
                Long.class,
                String.class,
                String.class,
                String.class,
                Principal.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        DeleteMapping revokeMapping = revoke.getAnnotation(DeleteMapping.class);
        Method replacePassword = PlatformAccountController.class.getDeclaredMethod(
                "replacePassword",
                Long.class,
                Long.class,
                AccountPasswordCredentialReplaceRequest.class,
                String.class,
                String.class,
                Principal.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        Method profile = PlatformAccountController.class.getDeclaredMethod(
                "profile", Long.class, Principal.class);
        Method updateProfile = PlatformAccountController.class.getDeclaredMethod(
                "updateProfile",
                Long.class,
                ManagedAccountProfileUpdateRequest.class,
                String.class,
                Principal.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        Method status = PlatformAccountController.class.getDeclaredMethod(
                "changeStatus", Long.class, ManagedAccountStatusRequest.class, String.class, Principal.class);
        Method delete = PlatformAccountController.class.getDeclaredMethod(
                "delete", Long.class, String.class, Principal.class);
        Method revokeAll = PlatformAccountController.class.getDeclaredMethod(
                "endSessions",
                Long.class,
                String.class,
                String.class,
                Principal.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        RequestHeader mfaIfMatch = (RequestHeader) mfa.getParameterAnnotations()[2][0];
        RequestHeader mfaStepUp = (RequestHeader) mfa.getParameterAnnotations()[3][0];
        RequestHeader revokeIfMatch = (RequestHeader) revoke.getParameterAnnotations()[2][0];

        assertThat(root.value()).containsExactly("/mg/platform/accounts");
        assertThat(sessions.value()).containsExactly("/{accountId}/sessions");
        assertThat(mfaMapping.value()).containsExactly("/{accountId}/second-factor/requirement");
        assertThat(recoveryCodes.getAnnotation(PostMapping.class).value()).containsExactly("/{accountId}/recovery-codes");
        assertThat(revokeMapping.value()).containsExactly("/{accountId}/sessions/{sessionId}");
        assertThat(replacePassword.getAnnotation(PutMapping.class).value())
                .containsExactly("/{accountId}/credentials/{credentialId}/password");
        assertThat(profile.getAnnotation(GetMapping.class).value()).containsExactly("/{accountId}/profile");
        assertThat(updateProfile.getAnnotation(PutMapping.class).value()).containsExactly("/{accountId}/profile");
        assertThat(status.getAnnotation(PatchMapping.class).value()).containsExactly("/{accountId}");
        assertThat(delete.getAnnotation(DeleteMapping.class).value()).containsExactly("/{accountId}");
        assertThat(revokeAll.getAnnotation(DeleteMapping.class).value()).containsExactly("/{accountId}/sessions");
        assertThat(mfaIfMatch.value()).isEqualTo(HttpHeaders.IF_MATCH);
        assertThat(mfaStepUp.value()).isEqualTo(AuthenticationHeaders.AUTHENTICATION_ID);
        assertThat(revokeIfMatch.value()).isEqualTo(HttpHeaders.IF_MATCH);
    }

    @Test
    void shouldUseAccountPolicyStepUpAndIfMatchForRecoveryCodeReissue() {
        AccountService accounts = mock(AccountService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AccountAccessPolicy accessPolicy = mock(AccountAccessPolicy.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IamAccountEntity target = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(true)
                .setVersion(4L);
        IamAccountEntity changed = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(true)
                .setVersion(5L);
        Principal principal = Principal.of(9L, com.sca4d.common.auth.PrincipalType.PLATFORM, 8L, null);
        Subject subject = new Subject(8L, "管理员");
        Account authenticatedAccount = new Account(7L, 8L, AccountScopeType.GLOBAL, null, false);
        when(accounts.require(80L)).thenReturn(target);
        when(browser.require(request)).thenReturn("browser-hash");
        when(security.reissueManagedRecoveryCodes(subject, authenticatedAccount, 80L, 4L, "step-up-1", "browser-hash"))
                .thenReturn(new AccountSecondFactorChange(
                        changed,
                        new RecoveryCodeSummary(1L, 80L, OffsetDateTime.parse("2026-07-18T10:00:00+08:00"), 10, 0L),
                        List.of("AAAAA-BBBBB-CCCCC-DDDDD")));
        PlatformAccountController controller = new PlatformAccountController(
                accounts,
                mock(AccountProvisioningService.class),
                security,
                accessPolicy,
                browser,
                mock(ManagedAccountProfileService.class),
                mock(AccountLifecycleService.class),
                mock(DefaultProfileService.class));

        var response = controller.reissueRecoveryCodes(
                80L, "\"4\"", "step-up-1", principal, subject, authenticatedAccount, request);

        assertThat(response.getData().getRecoveryCodes()).containsExactly("AAAAA-BBBBB-CCCCC-DDDDD");
        verify(accessPolicy).require(principal, target);
        verify(security).reissueManagedRecoveryCodes(
                subject, authenticatedAccount, 80L, 4L, "step-up-1", "browser-hash");
    }

    @Test
    void shouldUseAccountPolicyStepUpAndIfMatchForMfaChange() {
        AccountService accounts = mock(AccountService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AccountAccessPolicy accessPolicy = mock(AccountAccessPolicy.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IamAccountEntity target = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false)
                .setVersion(4L);
        IamAccountEntity changed = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(true)
                .setVersion(5L);
        AccountSecondFactorRequest body = new AccountSecondFactorRequest();
        body.setRequired(true);
        body.setVersion(4L);
        Principal principal = Principal.of(9L, com.sca4d.common.auth.PrincipalType.PLATFORM, 8L, null);
        Subject authenticatedSubject = new Subject(8L, "管理员");
        Account authenticatedAccount = new Account(7L, 8L, AccountScopeType.GLOBAL, null, false);
        RecoveryCodeSummary summary = new RecoveryCodeSummary(
                1L, 80L, OffsetDateTime.parse("2026-07-18T10:00:00+08:00"), 10, 0L);
        when(accounts.require(80L)).thenReturn(target);
        when(browser.require(request)).thenReturn("browser-hash");
        when(security.changeManagedSecondFactorRequirement(
                authenticatedSubject,
                authenticatedAccount,
                80L,
                true,
                4L,
                "step-up-1",
                "browser-hash"))
                .thenReturn(new AccountSecondFactorChange(changed, summary, List.of("AAAAA-BBBBB-CCCCC-DDDDD")));
        PlatformAccountController controller = new PlatformAccountController(
                accounts,
                mock(AccountProvisioningService.class),
                security,
                accessPolicy,
                browser,
                mock(ManagedAccountProfileService.class),
                mock(AccountLifecycleService.class),
                mock(DefaultProfileService.class));

        var response = controller.changeSecondFactorRequirement(
                80L,
                body,
                "\"4\"",
                "step-up-1",
                principal,
                authenticatedSubject,
                authenticatedAccount,
                request);

        assertThat(response.getData().isRequired()).isTrue();
        assertThat(response.getData().getAccountVersion()).isEqualTo(5L);
        verify(accessPolicy).require(principal, target);
        verify(security).changeManagedSecondFactorRequirement(
                authenticatedSubject,
                authenticatedAccount,
                80L,
                true,
                4L,
                "step-up-1",
                "browser-hash");
    }

    @Test
    void shouldRevokeOnlyTargetAccountSessionAfterStepUp() {
        AccountService accounts = mock(AccountService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AccountAccessPolicy accessPolicy = mock(AccountAccessPolicy.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IamAccountEntity target = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false)
                .setVersion(4L);
        Principal principal = Principal.of(9L, com.sca4d.common.auth.PrincipalType.PLATFORM, 8L, null);
        Subject authenticatedSubject = new Subject(8L, "管理员");
        Account authenticatedAccount = new Account(7L, 8L, AccountScopeType.GLOBAL, null, false);
        when(accounts.require(80L)).thenReturn(target);
        when(browser.require(request)).thenReturn("browser-hash");
        PlatformAccountController controller = new PlatformAccountController(
                accounts,
                mock(AccountProvisioningService.class),
                security,
                accessPolicy,
                browser,
                mock(ManagedAccountProfileService.class),
                mock(AccountLifecycleService.class),
                mock(DefaultProfileService.class));

        var response = controller.endSession(
                80L,
                "session-1",
                "\"4\"",
                "step-up-1",
                principal,
                authenticatedSubject,
                authenticatedAccount,
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(accessPolicy).require(principal, target);
        verify(security).endManagedSession(
                authenticatedSubject,
                authenticatedAccount,
                80L,
                "session-1",
                "step-up-1",
                "browser-hash");
    }

    @Test
    void shouldResetPasswordWithCredentialIfMatchAndStepUp() {
        AccountService accounts = mock(AccountService.class);
        AccountSecurityService security = mock(AccountSecurityService.class);
        AccountAccessPolicy accessPolicy = mock(AccountAccessPolicy.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IamAccountEntity target = new IamAccountEntity()
                .setId(80L)
                .setSubjectId(70L)
                .setScopeType(AccountScopeType.GLOBAL)
                .setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false)
                .setVersion(4L);
        Principal principal = Principal.of(9L, com.sca4d.common.auth.PrincipalType.PLATFORM, 8L, null);
        Subject authenticatedSubject = new Subject(8L, "管理员");
        Account authenticatedAccount = new Account(7L, 8L, AccountScopeType.GLOBAL, null, false);
        AccountPasswordCredentialReplaceRequest body = new AccountPasswordCredentialReplaceRequest();
        body.setPassword("new-secret");
        when(accounts.require(80L)).thenReturn(target);
        when(browser.require(request)).thenReturn("browser-hash");
        PlatformAccountController controller = new PlatformAccountController(
                accounts,
                mock(AccountProvisioningService.class),
                security,
                accessPolicy,
                browser,
                mock(ManagedAccountProfileService.class),
                mock(AccountLifecycleService.class),
                mock(DefaultProfileService.class));

        controller.replacePassword(
                80L,
                90L,
                body,
                "\"3\"",
                "step-up-1",
                principal,
                authenticatedSubject,
                authenticatedAccount,
                request);

        verify(accessPolicy).require(principal, target);
        verify(security).replaceManagedPassword(
                authenticatedSubject,
                authenticatedAccount,
                80L,
                90L,
                3L,
                body.credential(),
                "step-up-1",
                "browser-hash");
    }
}
