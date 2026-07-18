package com.sca4d.mail;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个 family 和 locale 下可独立启停的邮件模板。 */
@Getter
@AllArgsConstructor
public class MailTemplate {

    private final Long templateId;
    private final String family;
    private final String locale;
    private final String subjectTemplate;
    private final String textTemplate;
    private final String htmlTemplate;
    private final MailTemplateStatus status;
    private final Long version;
}
