package com.sca4d.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthMethodCatalog;
import com.sca4d.authentication.AuthenticationBrowser;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.authentication.email.EmailCodeChallengeService;
import com.sca4d.authentication.recovery.AccountRecoveryService;
import com.sca4d.authentication.request.SessionCreateRequest;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.test.TestSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 验证登录成功后以最终创建的 Session 记录审计身份和目标账号。 */
class AuthenticationControllerTest {

    @AfterEach
    void clearCurrentRequest() {
        CurrentRequestHolder.clear();
    }

    @Test
    void shouldWriteCreatedSessionIntoCurrentRequestForFinalLoginAudit() throws Exception {
        AuthenticationProcessService authentications = mock(AuthenticationProcessService.class);
        AuthenticationBrowser browser = mock(AuthenticationBrowser.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        TestSession session = TestSession.of(81L);
        SessionCreateRequest body = new SessionCreateRequest();
        body.setAuthenticationId("authentication-1");
        when(browser.require(servletRequest)).thenReturn("browser-hash");
        when(authentications.session("authentication-1", "browser-hash")).thenReturn(session);
        CurrentRequest currentRequest = new CurrentRequest();
        CurrentRequestHolder.set(currentRequest);
        AuthenticationController controller = new AuthenticationController(
                authentications,
                mock(AccountRecoveryService.class),
                mock(EmailCodeChallengeService.class),
                mock(AuthMethodCatalog.class),
                browser,
                mock(Authentication.class));

        var response = controller.createSession(body, servletRequest);

        assertThat(response.getData().getAccountId()).isEqualTo(81L);
        assertThat(currentRequest.getSession()).isSameAs(session);
        assertThat(currentRequest.getSubject()).isSameAs(session.getSubject());
        assertThat(currentRequest.getAccount()).isSameAs(session.getAccount());
        verify(authentications).session("authentication-1", "browser-hash");

        Audit audit = AuthenticationController.class
                .getDeclaredMethod("createSession", SessionCreateRequest.class, HttpServletRequest.class)
                .getAnnotation(Audit.class);
        assertThat(audit.module()).isEqualTo(AuthenticationAuditOperations.LOGIN);
        assertThat(audit.action()).isEqualTo(AuthenticationAuditOperations.SESSION);
        assertThat(audit.targetType()).isEqualTo(IamResourceTypes.ACCOUNT);
        assertThat(audit.targetId()).isEqualTo("#result?.data?.accountId");
        assertThat(audit.summary()).contains("audit.summary.authentication.login.session.success");
    }
}
