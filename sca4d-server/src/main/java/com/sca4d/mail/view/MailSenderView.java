package com.sca4d.mail.view;

import com.sca4d.mail.MailSender;
import com.sca4d.mail.MailSenderStatus;
import com.sca4d.mail.SmtpSecurity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 不包含 SMTP 密码、key-id、nonce 或密文的邮件发送配置视图。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MailSenderView {

    private Long senderId;
    private String name;
    private String host;
    private Integer port;
    private SmtpSecurity security;
    private String username;
    private Boolean passwordConfigured;
    private String fromAddress;
    private String fromName;
    private String replyTo;
    private MailSenderStatus status;
    private Long version;

    /** @return 只包含管理页面允许读取的字段 */
    public static @NotNull MailSenderView from(@NotNull MailSender sender) {
        return new MailSenderView(
                sender.getSenderId(),
                sender.getName(),
                sender.getHost(),
                sender.getPort(),
                sender.getSecurity(),
                sender.getUsername(),
                sender.getPasswordConfigured(),
                sender.getFromAddress(),
                sender.getFromName(),
                sender.getReplyTo(),
                sender.getStatus(),
                sender.getVersion());
    }
}
