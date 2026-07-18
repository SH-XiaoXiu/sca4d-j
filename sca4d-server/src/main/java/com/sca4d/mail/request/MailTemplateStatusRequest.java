package com.sca4d.mail.request;

import com.sca4d.common.validation.Required;
import com.sca4d.mail.MailTemplateStatus;
import lombok.Data;

/** 启用或停用邮件模板的乐观锁请求。 */
@Data
public class MailTemplateStatusRequest {

    @Required
    private MailTemplateStatus status;

}
