package com.sca4d.notification.email.sender;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.notification.email.EmailAttachment;
import com.sca4d.notification.email.EmailNotificationErrorCode;
import com.sca4d.notification.email.RenderedEmail;
import com.sca4d.notification.email.config.EmailNotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** 使用 JavaMail 和 SecretKeyring 建立三种 TLS 模式的 SMTP 连接。 */
public class SmtpEmailGateway implements EmailGateway {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpEmailGateway.class);

    private final @NotNull SecretKeyring keyring;

    private final @NotNull EmailNotificationProperties properties;

    /** @param keyring 解密 SMTP 密码的密钥环 @param properties SMTP 超时配置 */
    public SmtpEmailGateway(
            @NotNull SecretKeyring keyring,
            @NotNull EmailNotificationProperties properties) {
        this.keyring = Objects.requireNonNull(keyring, "SecretKeyring 不能为空");
        this.properties = Objects.requireNonNull(properties, "EmailNotificationProperties 不能为空");
    }

    @Override
    public void testConnection(@NotNull EmailSenderConfiguration configuration) {
        EmailSenderConfiguration checked = Objects.requireNonNull(configuration, "SMTP 配置不能为空");
        try {
            mailSender(checked).testConnection();
            LOG.info("SMTP 连接测试成功 | senderId={} security={}", checked.sender().senderId(), checked.sender().security());
        } catch (MessagingException | MailException exception) {
            throw new BusinessException(EmailNotificationErrorCode.SMTP_FAILED, exception);
        }
    }

    @Override
    public void send(
            @NotNull EmailSenderConfiguration configuration,
            @NotNull String recipient,
            @NotNull RenderedEmail email) {
        send(configuration, recipient, email, List.of());
    }

    /** 使用 MIME multipart 发送带可选附件的 Email。 */
    @Override
    public void send(
            @NotNull EmailSenderConfiguration configuration,
            @NotNull String recipient,
            @NotNull RenderedEmail email,
            @NotNull List<EmailAttachment> attachments) {
        EmailSenderConfiguration checked = Objects.requireNonNull(configuration, "SMTP 配置不能为空");
        try {
            JavaMailSenderImpl sender = mailSender(checked);
            MimeMessage message = sender.createMimeMessage();
            EmailSender senderConfiguration = checked.sender();
            String html = email.html();
            String fromName = senderConfiguration.fromName();
            List<EmailAttachment> checkedAttachments = List.copyOf(attachments);
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    html != null || !checkedAttachments.isEmpty(),
                    StandardCharsets.UTF_8.name());
            if (fromName == null) {
                helper.setFrom(senderConfiguration.fromAddress());
            } else {
                helper.setFrom(senderConfiguration.fromAddress(), fromName);
            }
            String replyTo = senderConfiguration.replyTo();
            if (replyTo != null) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(recipient);
            helper.setSubject(email.subject());
            if (html == null) {
                helper.setText(email.text(), false);
            } else {
                helper.setText(email.text(), html);
            }
            for (EmailAttachment attachment : checkedAttachments) {
                helper.addAttachment(
                        attachment.filename(),
                        new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            sender.send(message);
            LOG.info(
                    "Email 已由 SMTP 接受 | senderId={} security={}",
                    checked.sender().senderId(),
                    checked.sender().security());
        } catch (MessagingException | MailException | UnsupportedEncodingException exception) {
            throw new BusinessException(EmailNotificationErrorCode.SMTP_FAILED, exception);
        }
    }

    /** 创建一次操作独享且已经设置 TLS、超时和可选认证的 JavaMailSender。 */
    private @NotNull JavaMailSenderImpl mailSender(@NotNull EmailSenderConfiguration configuration) {
        EmailSender sender = configuration.sender();
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(sender.host());
        mail.setPort(sender.port());
        mail.setProtocol(sender.security() == SmtpSecurity.TLS ? "smtps" : "smtp");
        Properties javaMail = mail.getJavaMailProperties();
        String prefix = sender.security() == SmtpSecurity.TLS ? "mail.smtps." : "mail.smtp.";
        javaMail.put(prefix + "connectiontimeout", Long.toString(properties.getConnectionTimeout().toMillis()));
        javaMail.put(prefix + "timeout", Long.toString(properties.getReadTimeout().toMillis()));
        javaMail.put(prefix + "writetimeout", Long.toString(properties.getWriteTimeout().toMillis()));
        if (sender.security() == SmtpSecurity.STARTTLS) {
            javaMail.put("mail.smtp.starttls.enable", "true");
            javaMail.put("mail.smtp.starttls.required", "true");
        }
        if (sender.username() != null) {
            mail.setUsername(sender.username());
            EncryptedSecret encrypted = Objects.requireNonNull(configuration.password(), "SMTP 密码密文不能为空");
            SecretCipher cipher = keyring.findCipher(encrypted.keyId())
                    .orElseThrow(() -> new IllegalStateException("SMTP 密码使用的密钥不存在: " + encrypted.keyId()));
            byte[] plaintext = cipher.decrypt(passwordPurpose(sender.senderId()), encrypted);
            try {
                mail.setPassword(new String(plaintext, StandardCharsets.UTF_8));
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
        return mail;
    }

    /** @return SMTP 密码加解密的用途隔离键 */
    public static @NotNull String passwordPurpose(long senderId) {
        return "notification.email.sender." + senderId + ".password";
    }
}
