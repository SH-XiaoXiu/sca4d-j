package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.mail.request.MailSenderCreateRequest;
import com.sca4d.mail.view.MailSenderView;
import org.junit.jupiter.api.Test;

/** 验证 SMTP 管理响应只公开密码是否已配置。 */
class MailSenderViewTest {

    @Test
    void shouldNotExposePasswordEncryptionColumns() throws Exception {
        MailSender sender = new MailSender(
                123L,
                "Primary",
                "mail.example.com",
                587,
                SmtpSecurity.STARTTLS,
                "alice",
                true,
                "no-reply@example.com",
                "Example",
                "support@example.com",
                MailSenderStatus.DISABLED,
                7L);

        String json = new ObjectMapper().writeValueAsString(MailSenderView.from(sender));

        assertThat(json)
                .contains("\"passwordConfigured\":true")
                .doesNotContain(
                        "passwordKeyId",
                        "passwordNonce",
                        "passwordCiphertext",
                        "secret-password");
    }

    @Test
    void shouldNotIncludePlaintextPasswordInRequestStringOrHash() {
        MailSenderCreateRequest request = new MailSenderCreateRequest();
        request.setName("Primary");
        request.setPassword("secret-password");
        MailSenderCreateRequest samePublicFields = new MailSenderCreateRequest();
        samePublicFields.setName("Primary");
        samePublicFields.setPassword("another-password");

        assertThat(request.toString()).doesNotContain("secret-password");
        assertThat(request).isEqualTo(samePublicFields);
        assertThat(request.hashCode()).isEqualTo(samePublicFields.hashCode());
    }
}
