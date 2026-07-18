package com.sca4d.mail.request;

import com.sca4d.common.validation.Required;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 用草稿内容和示例变量渲染邮件预览的请求。 */
@Data
public class MailTemplatePreviewRequest {

    @NotBlank
    @Size(max = 512)
    private String subjectTemplate;

    @NotBlank
    @Size(max = 100_000)
    private String textTemplate;

    @Size(max = 100_000)
    private String htmlTemplate;

    @Valid
    @Required
    @Size(max = 50)
    private Map<@Pattern(regexp = "[a-z][a-zA-Z0-9_]{0,63}") String, @Size(max = 10_000) String> variables;
}
