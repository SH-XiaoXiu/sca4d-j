package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.app.server.authentication.view.EmailCodeChallengeView;
import com.sca4d.app.server.system.view.EmailSenderView;
import com.sca4d.auth.email.EmailCodeChallenge;
import com.sca4d.auth.email.EmailCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.notification.email.sender.EmailSender;
import com.sca4d.notification.email.sender.SmtpSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

/** 校验 Email Code 与 SMTP 管理响应不序列化认证秘密或持久化密文。 */
class EmailHttpContractTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Email Code Challenge Response Does Not Expose Code Or Account")
    void emailCodeChallengeResponseDoesNotExposeCodeOrAccount() {
        EmailCodeChallenge challenge = new EmailCodeChallenge(
                new EmailCodeChallengeId(UUID.randomUUID().toString()),
                "a***@e***.com",
                Instant.parse("2026-08-07T00:05:00Z"),
                Instant.parse("2026-08-07T00:01:00Z"));

        JsonNode response = json.valueToTree(EmailCodeChallengeView.from(challenge));

        assertThat(response.has("challengeId")).isTrue();
        assertThat(response.has("maskedRecipient")).isTrue();
        assertThat(response.has("code")).isFalse();
        assertThat(response.has("accountId")).isFalse();
        assertThat(response.has("email")).isFalse();
    }

    @Test
    @DisplayName("Email Sender Response Exposes Only Password Configuration State")
    void emailSenderResponseExposesOnlyPasswordConfigurationState() {
        EmailSender sender = new EmailSender(
                42,
                "Primary",
                "smtp.example.com",
                465,
                SmtpSecurity.TLS,
                "mailer@example.com",
                true,
                "mailer@example.com",
                "Example",
                null,
                true,
                new Version(3));

        JsonNode response = json.valueToTree(EmailSenderView.from(sender));

        assertThat(response.path("passwordConfigured").asBoolean()).isTrue();
        assertThat(response.has("password")).isFalse();
        assertThat(response.has("passwordCiphertext")).isFalse();
        assertThat(response.has("passwordKeyId")).isFalse();
    }
}
