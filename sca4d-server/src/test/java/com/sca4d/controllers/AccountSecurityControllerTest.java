package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationHeaders;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.SessionSnapshot;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorChange;
import com.sca4d.iam.account.AccountSecurity;
import com.sca4d.iam.account.AccountSecurityService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.request.AccountSecondFactorRequest;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** 验证账号安全资源的 REST 路由、响应转换和 STEP_UP Header。 */
class AccountSecurityControllerTest {

    @Test
    void shouldExposeCurrentAccountSecurityResources() throws Exception {
        RequestMapping root = AccountSecurityController.class.getAnnotation(RequestMapping.class);
        GetMapping accounts = AccountSecurityController.class
                .getDeclaredMethod("accounts", Subject.class)
                .getAnnotation(GetMapping.class);
        GetMapping sessions = AccountSecurityController.class
                .getDeclaredMethod("sessions", Long.class, Subject.class)
                .getAnnotation(GetMapping.class);
        Method secondFactor = AccountSecurityController.class.getDeclaredMethod(
                "changeSecondFactorRequirement",
                Long.class,
                String.class,
                AccountSecondFactorRequest.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        PutMapping put = secondFactor.getAnnotation(PutMapping.class);
        Method endSession = AccountSecurityController.class.getDeclaredMethod(
                "endSession",
                Long.class,
                String.class,
                String.class,
                Subject.class,
                Account.class,
                HttpServletRequest.class);
        DeleteMapping delete = endSession.getAnnotation(DeleteMapping.class);
        RequestHeader stepUp = (RequestHeader) endSession.getParameterAnnotations()[2][0];

        assertThat(root.value()).containsExactly("/me/accounts");
        assertThat(AccountSecurityController.class.getAnnotation(AuthPrincipal.class).principalRequired()).isTrue();
        assertThat(accounts.value()).isEmpty();
        assertThat(sessions.value()).containsExactly("/{accountId}/sessions");
        assertThat(put.value()).containsExactly("/{accountId}/second-factor/requirement");
        assertThat(delete.value()).containsExactly("/{accountId}/sessions/{sessionId}");
        assertThat(stepUp.value()).isEqualTo(AuthenticationHeaders.AUTHENTICATION_ID);
        assertThat(stepUp.required()).isTrue();
    }

    @Test
    void shouldReturnAccountsAndSessionsWithoutAuthenticationSecrets() {
        AccountSecurityService service = mock(AccountSecurityService.class);
        Subject subject = new Subject(10L, "管理员");
        IamAccountEntity storedAccount = new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(0L);
        SessionSnapshot session = new SessionSnapshot(
                "session-1", 30L, "browser-1", "Chrome", Instant.parse("2026-07-17T00:00:00Z"));
        when(service.accounts(10L))
                .thenReturn(List.of(new AccountSecurity(storedAccount, List.of(), List.of(), List.of(), null)));
        when(service.sessions(10L, 30L)).thenReturn(List.of(session));
        AccountSecurityController controller = new AccountSecurityController(service, mock(AuthenticationBrowser.class));

        var accounts = controller.accounts(subject).getData();
        var sessions = controller.sessions(30L, subject).getData();

        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst().getAccountId()).isEqualTo(30L);
        assertThat(accounts.getFirst().getIdentifiers()).isEmpty();
        assertThat(accounts.getFirst().getCredentials()).isEmpty();
        assertThat(accounts.getFirst().getPasskeyCredentials()).isEmpty();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().getSessionId()).isEqualTo("session-1");
        assertThat(sessions.getFirst().getTerminalName()).isEqualTo("Chrome");
    }

    @Test
    void shouldEndSessionWithBrowserBoundStepUpAndReturnNoContent() {
        AccountSecurityService service = mock(AccountSecurityService.class);
        AuthenticationBrowser authenticationBrowser = mock(AuthenticationBrowser.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Subject subject = new Subject(10L, "管理员");
        Account account = new Account(30L, 10L, AccountScopeType.GLOBAL, null, true);
        when(authenticationBrowser.require(request)).thenReturn("browser-hash");
        AccountSecurityController controller = new AccountSecurityController(service, authenticationBrowser);

        var response = controller.endSession(
                31L, "session-1", "flow-1", subject, account, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(service).endSession(
                subject, account, 31L, "session-1", "flow-1", "browser-hash");
    }

    @Test
    void shouldChangeSecondFactorRequirementWithBrowserBoundStepUp() {
        AccountSecurityService service = mock(AccountSecurityService.class);
        AuthenticationBrowser authenticationBrowser = mock(AuthenticationBrowser.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Subject subject = new Subject(10L, "管理员");
        Account authenticatedAccount = new Account(30L, 10L, AccountScopeType.GLOBAL, null, false);
        IamAccountEntity updated = new IamAccountEntity().setId(31L).setSubjectId(10L).setScopeType(AccountScopeType.TENANT).setScopeId(20L)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(5L);
        RecoveryCodeSummary summary = new RecoveryCodeSummary(
                301L, 31L, OffsetDateTime.parse("2026-07-17T09:00:00+08:00"), 10, 0L);
        AccountSecondFactorRequest request = new AccountSecondFactorRequest();
        request.setRequired(true);
        request.setVersion(4L);
        when(authenticationBrowser.require(servletRequest)).thenReturn("browser-hash");
        when(service.changeSecondFactorRequirement(
                subject,
                authenticatedAccount,
                31L,
                true,
                4L,
                "flow-2",
                "browser-hash"))
                .thenReturn(new AccountSecondFactorChange(
                        updated, summary, List.of("AAAAA-BBBBB-CCCCC-DDDDD")));
        AccountSecurityController controller = new AccountSecurityController(service, authenticationBrowser);

        var response = controller.changeSecondFactorRequirement(
                31L, "flow-2", request, subject, authenticatedAccount, servletRequest);

        assertThat(response.getData().isRequired()).isTrue();
        assertThat(response.getData().getAccountVersion()).isEqualTo(5L);
        assertThat(response.getData().getRecoveryCodes())
                .containsExactly("AAAAA-BBBBB-CCCCC-DDDDD");
        assertThat(response.getData().toString()).doesNotContain("AAAAA-BBBBB-CCCCC-DDDDD");
        verify(service).changeSecondFactorRequirement(
                subject,
                authenticatedAccount,
                31L,
                true,
                4L,
                "flow-2",
                "browser-hash");
    }
}
