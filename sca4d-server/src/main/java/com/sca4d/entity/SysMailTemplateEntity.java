package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.mail.MailTemplateStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** 邮件模板 family、locale、三种内容和生命周期的持久化实体。 */
@Data
@Accessors(chain = true)
@Table("sys_mail_template")
public class SysMailTemplateEntity {

    private Long id;
    private String family;
    private String locale;
    private String subjectTemplate;
    private String textTemplate;
    private String htmlTemplate;
    private MailTemplateStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
