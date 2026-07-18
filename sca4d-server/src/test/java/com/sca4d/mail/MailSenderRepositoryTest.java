package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.entity.SysMailSenderEntity;
import com.sca4d.mail.repository.MailSenderRepository;
import com.sca4d.mapper.MailSenderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证 SMTP 密文列恢复、写入和乐观锁结果转换。 */
class MailSenderRepositoryTest {

    @Test
    void shouldRebuildCompleteEncryptedPassword() {
        MailSenderMapper mapper = mock(MailSenderMapper.class);
        when(mapper.selectOneByQuery(any())).thenReturn(entity()
                .setPasswordKeyId("primary")
                .setPasswordNonce(new byte[12])
                .setPasswordCiphertext(new byte[] {1, 2}));

        var configuration = new MailSenderRepository(mapper).findConfiguration(123L);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getSender().getPasswordConfigured()).isTrue();
        assertThat(configuration.getPassword().getKeyId()).isEqualTo("primary");
        assertThat(configuration.getPassword().getCiphertext()).containsExactly(1, 2);
    }

    @Test
    void shouldRejectPartiallyMissingEncryptedPasswordColumns() {
        MailSenderMapper mapper = mock(MailSenderMapper.class);
        when(mapper.selectOneByQuery(any())).thenReturn(entity()
                .setPasswordKeyId("primary")
                .setPasswordNonce(new byte[12]));

        assertThatThrownBy(() -> new MailSenderRepository(mapper).findConfiguration(123L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SMTP 密码密文列不完整: 123");
    }

    @Test
    void shouldWriteEncryptedPasswordWithoutPlaintextField() {
        MailSenderMapper mapper = mock(MailSenderMapper.class);
        when(mapper.insertSender(any())).thenReturn(1);
        EncryptedSecret encrypted = new EncryptedSecret(
                "primary", new byte[12], new byte[] {1, 2});

        new MailSenderRepository(mapper).createSender(
                123L,
                "Primary",
                "mail.example.com",
                587,
                SmtpSecurity.STARTTLS,
                "alice",
                encrypted,
                "no-reply@example.com",
                null,
                null,
                MailSenderStatus.DISABLED);

        ArgumentCaptor<SysMailSenderEntity> captor = ArgumentCaptor.forClass(SysMailSenderEntity.class);
        verify(mapper).insertSender(captor.capture());
        assertThat(captor.getValue().getPasswordKeyId()).isEqualTo("primary");
        assertThat(captor.getValue().getPasswordNonce()).hasSize(12);
        assertThat(captor.getValue().getPasswordCiphertext()).containsExactly(1, 2);
        assertThat(captor.getValue().getStatus()).isEqualTo(MailSenderStatus.DISABLED);
    }

    @Test
    void shouldTranslateVersionedUpdateMissToConcurrencyConflict() {
        MailSenderMapper mapper = mock(MailSenderMapper.class);
        when(mapper.updateStatus(123L, MailSenderStatus.DISABLED, MailSenderStatus.ACTIVE, 7L))
                .thenReturn(0);

        assertThatThrownBy(() -> new MailSenderRepository(mapper)
                .updateStatus(123L, MailSenderStatus.DISABLED, MailSenderStatus.ACTIVE, 7L))
                .isInstanceOfSatisfying(
                        ConcurrencyConflictException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MailErrorCode.SENDER_CHANGED));
    }

    /** @return 不包含密码三列的 SMTP 持久化测试数据 */
    private SysMailSenderEntity entity() {
        return new SysMailSenderEntity()
                .setId(123L)
                .setName("Primary")
                .setHost("mail.example.com")
                .setPort(587)
                .setSecurity(SmtpSecurity.STARTTLS)
                .setUsername("alice")
                .setFromAddress("no-reply@example.com")
                .setStatus(MailSenderStatus.DISABLED)
                .setVersion(7L)
                .setDeleted(0);
    }
}
