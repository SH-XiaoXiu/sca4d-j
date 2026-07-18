package com.sca4d.mail;

import com.sca4d.common.web.ClientStateTarget;

/** 邮件模块写入客户端响应的固定状态目标。 */
public final class MailClientStateTargets {

    /** SMTP 邮件发送配置。 */
    public static final ClientStateTarget SENDERS = ClientStateTarget.of("mail-senders");
    /** 邮件模板。 */
    public static final ClientStateTarget TEMPLATES = ClientStateTarget.of("mail-templates");

    private MailClientStateTargets() {
    }
}
