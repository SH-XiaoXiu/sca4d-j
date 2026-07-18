package com.sca4d.mail.request;

import com.sca4d.common.validation.Required;
import com.sca4d.mail.MailSenderStatus;
import lombok.Data;

/** 启用或停用 SMTP 配置的乐观锁请求。 */
@Data
public class MailSenderStatusRequest {

    @Required
    private MailSenderStatus status;

}
