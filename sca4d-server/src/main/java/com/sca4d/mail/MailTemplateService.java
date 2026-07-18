package com.sca4d.mail;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 管理、预览、测试发送和按 family/locale 选用邮件模板。 */
public interface MailTemplateService {

    /** @return 全部未删除邮件模板 */
    @NotNull
    List<MailTemplate> templates();

    /** @return 指定邮件模板 */
    @NotNull
    MailTemplate require(@NotNull Long templateId);

    /** @return 三种内容模板引用的变量名 */
    @NotNull
    Set<String> variables(@NotNull MailTemplate template);

    /** 创建默认停用的邮件模板。 */
    @NotNull
    Long create(
            @NotNull String family,
            @NotNull String locale,
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate);

    /** 修改邮件模板标识和内容。 */
    void update(
            @NotNull Long templateId,
            @NotNull String family,
            @NotNull String locale,
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate,
            long version);

    /** 启用或停用邮件模板。 */
    void changeStatus(
            @NotNull Long templateId, @NotNull MailTemplateStatus status, long version);

    /** 删除已经停用的邮件模板。 */
    void delete(@NotNull Long templateId, long version);

    /** 渲染尚未保存的邮件模板，供管理端预览。 */
    @NotNull
    RenderedMail preview(
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate,
            @NotNull Map<String, String> variables);

    /** 用已保存模板和指定 SMTP 配置发送测试邮件。 */
    void sendTest(
            @NotNull Long templateId,
            @NotNull Long senderId,
            @NotNull String recipient,
            @NotNull Map<String, String> variables);

    /** 按精确 family 和 locale 渲染一封业务邮件，只选用启用模板。 */
    @NotNull
    RenderedMail render(
            @NotNull String family,
            @NotNull String locale,
            @NotNull Map<String, String> variables);
}
