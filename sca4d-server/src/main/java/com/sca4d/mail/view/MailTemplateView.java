package com.sca4d.mail.view;

import com.sca4d.mail.MailTemplate;
import com.sca4d.mail.MailTemplateStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/** 邮件模板管理视图，包含编辑内容及其引用的简单变量名。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MailTemplateView {

    private Long templateId;
    private String family;
    private String locale;
    private String subjectTemplate;
    private String textTemplate;
    private String htmlTemplate;
    private List<String> variableNames;
    private MailTemplateStatus status;
    private Long version;

    /** @return 邮件模板及保持首次出现顺序的变量名 */
    public static @NotNull MailTemplateView from(
            @NotNull MailTemplate template, @NotNull Set<String> variables) {
        return new MailTemplateView(
                template.getTemplateId(),
                template.getFamily(),
                template.getLocale(),
                template.getSubjectTemplate(),
                template.getTextTemplate(),
                template.getHtmlTemplate(),
                List.copyOf(variables),
                template.getStatus(),
                template.getVersion());
    }
}
