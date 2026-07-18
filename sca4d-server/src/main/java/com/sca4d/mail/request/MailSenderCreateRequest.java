package com.sca4d.mail.request;

import com.sca4d.common.validation.Required;
import com.sca4d.common.validation.SafeText;
import com.sca4d.mail.SmtpSecurity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 创建默认停用 SMTP 配置的请求。 */
@Data
public class MailSenderCreateRequest {

    @NotBlank
    @SafeText(max = 128)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String host;

    @Required
    @Min(1)
    @Max(65535)
    private Integer port;

    @Required
    private SmtpSecurity security;

    @Size(max = 255)
    private String username;

    @Size(max = 1024)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String password;

    @NotBlank
    @Email
    @Size(max = 255)
    private String fromAddress;

    @SafeText(max = 128)
    private String fromName;

    @Email
    @Size(max = 255)
    private String replyTo;

}
