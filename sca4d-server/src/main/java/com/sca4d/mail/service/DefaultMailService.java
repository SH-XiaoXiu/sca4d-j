package com.sca4d.mail.service;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djMailProperties;
import com.sca4d.mail.MailErrorCode;
import com.sca4d.mail.MailSender;
import com.sca4d.mail.MailSenderStatus;
import com.sca4d.mail.MailService;
import com.sca4d.mail.SmtpSecurity;
import com.sca4d.mail.repository.MailSenderRepository;
import com.sca4d.mail.security.MailSenderConfiguration;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/** 从数据库读取一项 SMTP 配置并执行连接测试或单封邮件发送。 */
@Service
public class DefaultMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMailService.class);

    private final MailSenderRepository repository;
    private final SecretKeyring keyring;
    private final int connectionTimeoutMillis;
    private final int readTimeoutMillis;
    private final int writeTimeoutMillis;

    /**
     * @param repository SMTP 配置和密码密文读取入口
     * @param keyring SMTP 密码解密入口
     * @param properties 三种 SMTP socket 超时
     */
    public DefaultMailService(
            @NotNull MailSenderRepository repository,
            @NotNull SecretKeyring keyring,
            @NotNull Sca4djMailProperties properties) {
        this.repository = repository;
        this.keyring = keyring;
        this.connectionTimeoutMillis = timeout(properties.getConnectionTimeout(), "connection-timeout");
        this.readTimeoutMillis = timeout(properties.getReadTimeout(), "read-timeout");
        this.writeTimeoutMillis = timeout(properties.getWriteTimeout(), "write-timeout");
    }

    @Override
    public void testConnection(@NotNull Long senderId) {
        long startedAt = System.nanoTime();
        MailSenderConfiguration configuration = repository.findConfiguration(senderId);
        if (configuration == null) {
            throw new BizException(MailErrorCode.SENDER_NOT_FOUND);
        }
        try {
            mailSender(configuration).testConnection();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "邮件服务器连接测试完成 | senderId={} durationMs={} host={}",
                    senderId,
                    durationMs,
                    configuration.getSender().getHost());
        } catch (MessagingException | MailException exception) {
            throw new BizException(MailErrorCode.CONNECTION_FAILED, exception);
        }
    }

    @Override
    public void send(
            @NotNull Long senderId,
            @NotNull String recipient,
            @NotNull String subject,
            @NotNull String text,
            @Nullable String html) {
        long startedAt = System.nanoTime();
        MailSenderConfiguration configuration = repository.findConfiguration(senderId);
        if (configuration == null) {
            throw new BizException(MailErrorCode.SENDER_NOT_FOUND);
        }
        if (configuration.getSender().getStatus() != MailSenderStatus.ACTIVE) {
            throw new BizException(MailErrorCode.SENDER_DISABLED);
        }
        try {
            InternetAddress to = new InternetAddress(recipient, true);
            to.validate();
            JavaMailSenderImpl sender = mailSender(configuration);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, html != null, StandardCharsets.UTF_8.name());
            MailSender current = configuration.getSender();
            helper.setTo(to);
            helper.setSubject(subject);
            if (current.getFromName() == null) {
                helper.setFrom(current.getFromAddress());
            } else {
                helper.setFrom(current.getFromAddress(), current.getFromName());
            }
            if (current.getReplyTo() != null) {
                helper.setReplyTo(current.getReplyTo());
            }
            if (html == null) {
                helper.setText(text);
            } else {
                helper.setText(text, html);
            }
            sender.send(message);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "邮件发送完成 | senderId={} contentType={} durationMs={} host={}",
                    senderId,
                    html == null ? "text" : "html",
                    durationMs,
                    current.getHost());
        } catch (MessagingException | MailException | UnsupportedEncodingException exception) {
            throw new BizException(MailErrorCode.SEND_FAILED, exception);
        }
    }

    /**
     * 把数据库配置转换为一次调用独享的 JavaMailSender，不在进程内保存解密密码。
     *
     * @param configuration SMTP 配置和可选密码密文
     * @return 已设置认证、TLS 和 socket 超时的发送器
     */
    private @NotNull JavaMailSenderImpl mailSender(
            @NotNull MailSenderConfiguration configuration) {
        MailSender current = configuration.getSender();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setHost(current.getHost());
        sender.setPort(current.getPort());
        sender.setUsername(current.getUsername());
        if (configuration.getPassword() != null) {
            sender.setPassword(keyring.decrypt(
                    "smtp.sender." + current.getSenderId() + ".password",
                    configuration.getPassword()));
        }
        Properties javaMail = sender.getJavaMailProperties();
        javaMail.setProperty("mail.smtp.auth", Boolean.toString(current.getUsername() != null));
        javaMail.setProperty("mail.smtp.connectiontimeout", Integer.toString(connectionTimeoutMillis));
        javaMail.setProperty("mail.smtp.timeout", Integer.toString(readTimeoutMillis));
        javaMail.setProperty("mail.smtp.writetimeout", Integer.toString(writeTimeoutMillis));
        if (current.getSecurity() == SmtpSecurity.STARTTLS) {
            javaMail.setProperty("mail.smtp.starttls.enable", "true");
            javaMail.setProperty("mail.smtp.starttls.required", "true");
        } else if (current.getSecurity() == SmtpSecurity.IMPLICIT_TLS) {
            javaMail.setProperty("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }

    /**
     * 把正数 Duration 转换为 Jakarta Mail 使用的毫秒整数。
     *
     * @param value 当前超时配置
     * @param name 无效配置错误中显示的属性名
     * @return 1 到 Integer.MAX_VALUE 的毫秒数
     */
    private int timeout(@Nullable Duration value, @NotNull String name) {
        if (value == null || value.isZero() || value.isNegative() || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalStateException("sca4dj.mail." + name + " 必须是可转换为毫秒整数的正数 Duration");
        }
        return Math.toIntExact(value.toMillis());
    }
}
