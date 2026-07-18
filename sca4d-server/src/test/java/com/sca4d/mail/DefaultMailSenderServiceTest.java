package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.mail.repository.MailSenderRepository;
import com.sca4d.mail.service.DefaultMailSenderService;
import org.junit.jupiter.api.Test;

/** 验证 SMTP 认证组合、密码只写、状态和乐观锁规则。 */
class DefaultMailSenderServiceTest {

    @Test
    void shouldEncryptAuthenticatedPasswordWithSenderIdAndCreateDisabledConfiguration() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        SecretKeyring keyring = mock(SecretKeyring.class);
        IdGenerator ids = mock(IdGenerator.class);
        EncryptedSecret encrypted = new EncryptedSecret("primary", new byte[12], new byte[] {1});
        when(ids.nextId()).thenReturn(123L);
        when(keyring.encrypt("smtp.sender.123.password", "secret-password"))
                .thenReturn(encrypted);
        DefaultMailSenderService service = new DefaultMailSenderService(repository, keyring, ids);

        assertThat(service.create(
                " Primary ",
                " mail.example.com ",
                587,
                SmtpSecurity.STARTTLS,
                " alice ",
                "secret-password",
                " no-reply@example.com ",
                " Example ",
                " support@example.com "))
                .isEqualTo(123L);

        verify(repository).createSender(
                123L,
                "Primary",
                "mail.example.com",
                587,
                SmtpSecurity.STARTTLS,
                "alice",
                encrypted,
                "no-reply@example.com",
                "Example",
                "support@example.com",
                MailSenderStatus.DISABLED);
    }

    @Test
    void shouldPreservePasswordWhenUpdateOmitsPassword() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        SecretKeyring keyring = mock(SecretKeyring.class);
        when(repository.findSender(123L)).thenReturn(sender(MailSenderStatus.DISABLED, true, 7L));
        DefaultMailSenderService service = new DefaultMailSenderService(
                repository, keyring, mock(IdGenerator.class));

        service.update(
                123L,
                "Primary",
                "mail.example.com",
                587,
                SmtpSecurity.STARTTLS,
                "alice",
                null,
                false,
                "no-reply@example.com",
                null,
                null,
                7L);

        verify(keyring, never()).encrypt(any(), any());
        verify(repository).updateSender(
                123L,
                "Primary",
                "mail.example.com",
                587,
                SmtpSecurity.STARTTLS,
                "alice",
                false,
                null,
                "no-reply@example.com",
                null,
                null,
                7L);
    }

    @Test
    void shouldClearStoredPasswordWhenChangingToAnonymousConnection() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        when(repository.findSender(123L)).thenReturn(sender(MailSenderStatus.DISABLED, true, 7L));
        DefaultMailSenderService service = new DefaultMailSenderService(
                repository, mock(SecretKeyring.class), mock(IdGenerator.class));

        service.update(
                123L,
                "Mailpit",
                "localhost",
                1025,
                SmtpSecurity.NONE,
                null,
                null,
                false,
                "no-reply@example.com",
                null,
                null,
                7L);

        verify(repository).updateSender(
                123L,
                "Mailpit",
                "localhost",
                1025,
                SmtpSecurity.NONE,
                null,
                true,
                null,
                "no-reply@example.com",
                null,
                null,
                7L);
    }

    @Test
    void shouldRejectIncompleteAuthenticationAndStaleVersion() {
        DefaultMailSenderService service = new DefaultMailSenderService(
                mock(MailSenderRepository.class),
                mock(SecretKeyring.class),
                mock(IdGenerator.class));
        assertThatThrownBy(() -> service.create(
                "Primary",
                "mail.example.com",
                587,
                SmtpSecurity.STARTTLS,
                "alice",
                null,
                "no-reply@example.com",
                null,
                null))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.SENDER_AUTH_INVALID));

        MailSenderRepository repository = mock(MailSenderRepository.class);
        when(repository.findSender(123L)).thenReturn(sender(MailSenderStatus.DISABLED, false, 8L));
        DefaultMailSenderService versioned = new DefaultMailSenderService(
                repository, mock(SecretKeyring.class), mock(IdGenerator.class));
        assertThatThrownBy(() -> versioned.changeStatus(123L, MailSenderStatus.ACTIVE, 7L))
                .isInstanceOf(ConcurrencyConflictException.class);
        verify(repository, never()).updateStatus(any(), any(), any(), eq(7L));
    }

    @Test
    void shouldRequireDisabledStatusBeforeDelete() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        when(repository.findSender(123L)).thenReturn(sender(MailSenderStatus.ACTIVE, true, 7L));
        DefaultMailSenderService service = new DefaultMailSenderService(
                repository, mock(SecretKeyring.class), mock(IdGenerator.class));

        assertThatThrownBy(() -> service.delete(123L, 7L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.SENDER_ACTIVE));
        verify(repository, never()).deleteSender(any(), eq(7L));
    }

    /** @return 指定状态、密码标记和版本的 SMTP 配置测试数据 */
    private MailSender sender(
            MailSenderStatus status, boolean passwordConfigured, long version) {
        return new MailSender(
                123L,
                "Primary",
                "mail.example.com",
                587,
                SmtpSecurity.STARTTLS,
                passwordConfigured ? "alice" : null,
                passwordConfigured,
                "no-reply@example.com",
                null,
                null,
                status,
                version);
    }
}
