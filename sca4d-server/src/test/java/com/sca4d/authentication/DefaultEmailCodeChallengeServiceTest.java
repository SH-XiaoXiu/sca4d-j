package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.authentication.email.EmailCodeChallenge;
import com.sca4d.authentication.email.EmailCodeChallengeDelivery;
import com.sca4d.authentication.email.EmailCodeChallengeState;
import com.sca4d.authentication.email.service.DefaultEmailCodeChallengeService;
import com.sca4d.authentication.email.store.EmailCodeChallengeStore;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactStatus;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.mail.MailService;
import com.sca4d.mail.MailTemplateService;
import com.sca4d.mail.RenderedMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

/** 验证邮件验证码投递、敏感数据排除、失败取消和 Contact 确认顺序。 */
class DefaultEmailCodeChallengeServiceTest {

    private final EmailCodeChallengeStore store = mock(EmailCodeChallengeStore.class);
    private final ContactService contacts = mock(ContactService.class);
    private final MailTemplateService templates = mock(MailTemplateService.class);
    private final MailService mail = mock(MailService.class);
    private final Sca4djAuthenticationProperties properties = new Sca4djAuthenticationProperties();
    private final DefaultEmailCodeChallengeService service = new DefaultEmailCodeChallengeService(
            store, contacts, templates, mail, properties);

    @BeforeEach
    void configure() {
        properties.getEmailCode().setEnabled(true);
        properties.getEmailCode().setSenderId(7L);
    }

    @Test
    void shouldIssueHashedCodeWithoutExposingChallengeSecret() throws Exception {
        when(contacts.current(10L, ContactType.EMAIL)).thenReturn(new IamContactEntity().setId(30L).setSubjectId(10L).setContactType(ContactType.EMAIL)
                .setValue("alice@example.com").setVerifiedAt(null).setStatus(ContactStatus.ACTIVE).setVersion(3L));
        when(store.create(any(EmailCodeChallenge.class), any(), any())).thenReturn(true);
        when(templates.render(eq("authentication.email-code"), eq("zh-CN"), any()))
                .thenReturn(new RenderedMail("subject", "text", "<p>html</p>"));
        ArgumentCaptor<EmailCodeChallenge> challengeCaptor = ArgumentCaptor.forClass(EmailCodeChallenge.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);

        AuthenticationProcess flow = new AuthenticationProcess()
                .setAuthenticationId("flow-1")
                .setSubjectId(10L)
                .setAccountId(20L)
                .setScopeType(AccountScopeType.GLOBAL);
        EmailCodeChallengeDelivery delivery = service.issue(flow, Locale.SIMPLIFIED_CHINESE);

        verify(store).create(challengeCaptor.capture(), any(), any());
        verify(templates).render(eq("authentication.email-code"), eq("zh-CN"), variablesCaptor.capture());
        EmailCodeChallenge challenge = challengeCaptor.getValue();
        String code = variablesCaptor.getValue().get("code");
        assertThat(code).matches("\\d{6}");
        assertThat(challenge.getCodeHash()).startsWith("$argon2id$").doesNotContain(code);
        assertThat(delivery.getChallengeId()).isEqualTo(challenge.getChallengeId());
        assertThat(delivery.getMaskedRecipient()).isEqualTo("al***@example.com");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(delivery))
                .doesNotContain("codeHash", "$argon2id$", code);
        verify(mail).send(7L, "alice@example.com", "subject", "text", "<p>html</p>");
    }

    @Test
    void shouldCancelChallengeWhenDeliveryFails() {
        when(contacts.current(10L, ContactType.EMAIL)).thenReturn(new IamContactEntity().setId(30L).setSubjectId(10L).setContactType(ContactType.EMAIL)
                .setValue("alice@example.com").setVerifiedAt(null).setStatus(ContactStatus.ACTIVE).setVersion(3L));
        when(store.create(any(EmailCodeChallenge.class), any(), any())).thenReturn(true);
        when(templates.render(anyString(), anyString(), any()))
                .thenReturn(new RenderedMail("subject", "text", null));
        doThrow(new IllegalStateException("smtp failed"))
                .when(mail).send(7L, "alice@example.com", "subject", "text", null);
        ArgumentCaptor<EmailCodeChallenge> challengeCaptor = ArgumentCaptor.forClass(EmailCodeChallenge.class);

        AuthenticationProcess flow = new AuthenticationProcess()
                .setAuthenticationId("flow-1")
                .setSubjectId(10L)
                .setAccountId(20L)
                .setScopeType(AccountScopeType.GLOBAL);
        assertThatThrownBy(() -> service.issue(flow, Locale.ENGLISH))
                .isInstanceOf(IllegalStateException.class);

        verify(store).create(challengeCaptor.capture(), any(), any());
        verify(store).cancel("flow-1", challengeCaptor.getValue().getChallengeId());
    }

    @Test
    void shouldVerifyContactBeforeConsumingCorrectChallenge() {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        EmailCodeChallenge challenge = new EmailCodeChallenge()
                .setChallengeId("challenge-1")
                .setAuthenticationId("flow-1")
                .setSubjectId(10L)
                .setAccountId(20L)
                .setContactId(30L)
                .setContactVersion(3L)
                .setVerifiedContactVersion(4L)
                .setCodeHash(encoder.encode("123456"))
                .setState(EmailCodeChallengeState.PENDING)
                .setVersion(0L)
                .setAttemptsRemaining(5)
                .setExpiresAt(Instant.now().plusSeconds(300));
        when(store.find("flow-1", "challenge-1")).thenReturn(challenge);
        when(contacts.verify(10L, 30L, ContactType.EMAIL, 3L))
                .thenReturn(new IamContactEntity().setId(30L).setSubjectId(10L).setContactType(ContactType.EMAIL).setValue("alice@example.com")
                        .setVerifiedAt(OffsetDateTime.now()).setStatus(ContactStatus.ACTIVE).setVersion(4L));
        when(store.advance(challenge, 0L, EmailCodeChallengeState.PENDING, true)).thenReturn(true);

        EmailCodeChallenge consumed = service.consume("flow-1", "challenge-1", "123456");

        assertThat(consumed.getState()).isEqualTo(EmailCodeChallengeState.CONSUMED);
        assertThat(consumed.getVersion()).isEqualTo(1L);
        InOrder order = inOrder(contacts, store);
        order.verify(contacts).verify(10L, 30L, ContactType.EMAIL, 3L);
        order.verify(store).advance(challenge, 0L, EmailCodeChallengeState.PENDING, true);
    }

    @Test
    void shouldConsumeOneAttemptForIncorrectCode() {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        EmailCodeChallenge challenge = new EmailCodeChallenge()
                .setChallengeId("challenge-1")
                .setAuthenticationId("flow-1")
                .setSubjectId(10L)
                .setAccountId(20L)
                .setContactId(30L)
                .setContactVersion(3L)
                .setVerifiedContactVersion(4L)
                .setCodeHash(encoder.encode("123456"))
                .setState(EmailCodeChallengeState.PENDING)
                .setVersion(0L)
                .setAttemptsRemaining(5)
                .setExpiresAt(Instant.now().plusSeconds(300));
        when(store.find("flow-1", "challenge-1")).thenReturn(challenge);
        when(store.advance(challenge, 0L, EmailCodeChallengeState.PENDING, false)).thenReturn(true);

        assertThatThrownBy(() -> service.consume("flow-1", "challenge-1", "654321"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getErrorCode())
                        .isEqualTo(AuthenticationErrorCode.EMAIL_CODE_INVALID));

        assertThat(challenge.getAttemptsRemaining()).isEqualTo(4);
        assertThat(challenge.getVersion()).isEqualTo(1L);
        verify(store).advance(challenge, 0L, EmailCodeChallengeState.PENDING, false);
    }
}
