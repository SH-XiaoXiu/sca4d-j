package com.sca4d.mail.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建默认停用邮件模板的请求。 */
@Data
public class MailTemplateCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String family;

    @NotBlank
    @Size(max = 35)
    private String locale;

    @NotBlank
    @Size(max = 512)
    private String subjectTemplate;

    @NotBlank
    @Size(max = 100_000)
    private String textTemplate;

    @Size(max = 100_000)
    private String htmlTemplate;

}
