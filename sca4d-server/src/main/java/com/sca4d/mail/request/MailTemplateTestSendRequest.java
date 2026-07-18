package com.sca4d.mail.request;

import com.sca4d.common.validation.Required;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 使用已保存模板和指定 SMTP 配置发送一封测试邮件的请求。 */
@Data
public class MailTemplateTestSendRequest {

    @Required
    private Long senderId;

    @NotBlank
    @Email
    @Size(max = 255)
    private String recipient;

    @Valid
    @Required
    @Size(max = 50)
    private Map<@Pattern(regexp = "[a-z][a-zA-Z0-9_]{0,63}") String, @Size(max = 10_000) String> variables;
}
