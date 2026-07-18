package com.sca4d.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djMailProperties;
import com.sca4d.mail.repository.MailSenderRepository;
import com.sca4d.mail.security.MailSenderConfiguration;
import com.sca4d.mail.service.DefaultMailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 使用真实 Mailpit SMTP 和 HTTP API 验证文本与 HTML 邮件。 */
@EnabledIfEnvironmentVariable(named = "SCA4DJ_MAILPIT_ACCEPTANCE", matches = "true")
class MailpitAcceptanceTest {

    @Test
    void shouldSendTextAndHtmlThroughProductionMailService() throws Exception {
        String api = System.getenv().getOrDefault(
                "SCA4DJ_MAILPIT_API", "http://127.0.0.1:8025");
        String smtpHost = System.getenv().getOrDefault(
                "SCA4DJ_MAILPIT_SMTP_HOST", "127.0.0.1");
        int smtpPort = Integer.parseInt(System.getenv().getOrDefault(
                "SCA4DJ_MAILPIT_SMTP_PORT", "1025"));
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> cleared = http.send(
                HttpRequest.newBuilder(URI.create(api + "/api/v1/messages"))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(cleared.statusCode()).isEqualTo(200);

        MailSenderRepository repository = mock(MailSenderRepository.class);
        when(repository.findConfiguration(123L)).thenReturn(new MailSenderConfiguration(
                new MailSender(
                        123L,
                        "Mailpit",
                        smtpHost,
                        smtpPort,
                        SmtpSecurity.NONE,
                        null,
                        false,
                        "no-reply@example.test",
                        "SCA4D Test",
                        "support@example.test",
                        MailSenderStatus.ACTIVE,
                        1L),
                null));
        Sca4djMailProperties properties = new Sca4djMailProperties();
        properties.setConnectionTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setWriteTimeout(Duration.ofSeconds(10));
        DefaultMailService mail = new DefaultMailService(
                repository, mock(SecretKeyring.class), properties);

        mail.send(
                123L,
                "alice@example.test",
                "Mailpit acceptance",
                "Verification code: 123456",
                "<p>Verification code: <strong>123456</strong></p>");

        HttpResponse<String> list = http.send(
                HttpRequest.newBuilder(URI.create(api + "/api/v1/messages")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode messages = new ObjectMapper().readTree(list.body());
        assertThat(messages.path("messages_count").asInt()).isEqualTo(1);
        JsonNode summary = messages.path("messages").get(0);
        assertThat(summary.path("Subject").asText()).isEqualTo("Mailpit acceptance");
        String messageId = summary.path("ID").asText();

        HttpResponse<String> detail = http.send(
                HttpRequest.newBuilder(URI.create(api + "/api/v1/message/" + messageId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(detail.statusCode()).isEqualTo(200);
        JsonNode message = new ObjectMapper().readTree(detail.body());
        assertThat(message.path("Text").asText()).contains("Verification code: 123456");
        assertThat(message.path("HTML").asText()).contains("<strong>123456</strong>");
    }
}
