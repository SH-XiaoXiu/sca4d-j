package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djMailProperties;
import com.sca4d.mail.repository.MailSenderRepository;
import com.sca4d.mail.security.MailSenderConfiguration;
import com.sca4d.mail.service.DefaultMailService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 验证发送前状态检查和 SMTP 超时配置。 */
class DefaultMailServiceTest {

    @Test
    void shouldRejectDisabledSenderBeforeOpeningSmtpConnection() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        when(repository.findConfiguration(123L)).thenReturn(new MailSenderConfiguration(
                new MailSender(
                        123L,
                        "Mailpit",
                        "localhost",
                        1025,
                        SmtpSecurity.NONE,
                        null,
                        false,
                        "no-reply@example.com",
                        null,
                        null,
                        MailSenderStatus.DISABLED,
                        0L),
                null));
        DefaultMailService service = new DefaultMailService(
                repository, mock(SecretKeyring.class), properties());

        assertThatThrownBy(() -> service.send(
                123L, "alice@example.com", "Code", "123456", null))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.SENDER_DISABLED));
    }

    @Test
    void shouldRejectMissingSenderAndInvalidTimeout() {
        MailSenderRepository repository = mock(MailSenderRepository.class);
        DefaultMailService service = new DefaultMailService(
                repository, mock(SecretKeyring.class), properties());
        assertThatThrownBy(() -> service.testConnection(123L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.SENDER_NOT_FOUND));

        Sca4djMailProperties invalid = properties();
        invalid.setConnectionTimeout(Duration.ZERO);
        assertThatThrownBy(() -> new DefaultMailService(
                repository, mock(SecretKeyring.class), invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection-timeout");
    }

    /** @return 三种 socket 超时均有效的 SMTP 测试配置 */
    private Sca4djMailProperties properties() {
        Sca4djMailProperties properties = new Sca4djMailProperties();
        properties.setConnectionTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setWriteTimeout(Duration.ofSeconds(10));
        return properties;
    }
}
