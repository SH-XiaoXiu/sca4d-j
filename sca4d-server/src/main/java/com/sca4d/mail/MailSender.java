package com.sca4d.mail;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 不包含 SMTP 密码、key-id、nonce 或密文的邮件发送配置。 */
@Getter
@AllArgsConstructor
public class MailSender {

    private final Long senderId;
    private final String name;
    private final String host;
    private final Integer port;
    private final SmtpSecurity security;
    private final String username;
    private final Boolean passwordConfigured;
    private final String fromAddress;
    private final String fromName;
    private final String replyTo;
    private final MailSenderStatus status;
    private final Long version;
}
