package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.mail.MailSenderStatus;
import com.sca4d.mail.SmtpSecurity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** SMTP 地址、加密密码和默认发件资料的持久化实体。 */
@Data
@Accessors(chain = true)
@Table("sys_mail_sender")
public class SysMailSenderEntity {

    private Long id;
    private String name;
    private String host;
    private Integer port;
    private SmtpSecurity security;
    private String username;
    private String passwordKeyId;
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] passwordNonce;
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] passwordCiphertext;
    private String fromAddress;
    private String fromName;
    private String replyTo;
    private MailSenderStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
