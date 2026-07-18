package com.sca4d.mail;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成变量替换和 HTML 清洗、可以直接交给 SMTP 投递的邮件。 */
@Getter
@AllArgsConstructor
public class RenderedMail {

    private final String subject;
    private final String text;
    private final String html;
}
