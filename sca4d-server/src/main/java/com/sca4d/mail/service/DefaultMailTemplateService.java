package com.sca4d.mail.service;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.mail.MailClientStateTargets;
import com.sca4d.mail.MailErrorCode;
import com.sca4d.mail.MailService;
import com.sca4d.mail.MailTemplate;
import com.sca4d.mail.MailTemplateRenderer;
import com.sca4d.mail.MailTemplateService;
import com.sca4d.mail.MailTemplateStatus;
import com.sca4d.mail.RenderedMail;
import com.sca4d.mail.repository.MailTemplateRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 邮件模板管理、预览、测试发送和业务选用默认实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultMailTemplateService implements MailTemplateService {

    private static final Pattern FAMILY = Pattern.compile("[a-z][a-z0-9.-]{0,63}");
    private static final Pattern LOCALE = Pattern.compile("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*");

    private final MailTemplateRepository repository;
    private final MailTemplateRenderer renderer;
    private final MailService mailService;
    private final IdGenerator idGenerator;

    @Override
    public @NotNull List<MailTemplate> templates() {
        return repository.findTemplates();
    }

    @Override
    public @NotNull MailTemplate require(@NotNull Long templateId) {
        MailTemplate template = repository.findTemplate(templateId);
        if (template == null) {
            throw new BizException(MailErrorCode.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    @Override
    public @NotNull Set<String> variables(@NotNull MailTemplate template) {
        LinkedHashSet<String> variables = new LinkedHashSet<>();
        variables.addAll(renderer.variables(template.getSubjectTemplate()));
        variables.addAll(renderer.variables(template.getTextTemplate()));
        if (template.getHtmlTemplate() != null) {
            variables.addAll(renderer.variables(template.getHtmlTemplate()));
        }
        return Collections.unmodifiableSet(variables);
    }

    @Override
    public @NotNull Long create(
            @NotNull String family,
            @NotNull String locale,
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate) {
        String checkedFamily = normalizeFamily(family);
        String checkedLocale = normalizeLocale(locale);
        String checkedHtml = checkedHtmlTemplate(subjectTemplate, textTemplate, htmlTemplate);
        Long templateId = idGenerator.nextId();
        repository.createTemplate(
                templateId,
                checkedFamily,
                checkedLocale,
                subjectTemplate,
                textTemplate,
                checkedHtml);
        log.info(
                "邮件模板创建完成 | templateId={} status={} family={} locale={}",
                templateId,
                MailTemplateStatus.DISABLED,
                checkedFamily,
                checkedLocale);
        return templateId;
    }

    @Override
    public void update(
            @NotNull Long templateId,
            @NotNull String family,
            @NotNull String locale,
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate,
            long version) {
        MailTemplate current = require(templateId);
        if (!current.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.TEMPLATE_CHANGED, MailClientStateTargets.TEMPLATES);
        }
        repository.updateTemplate(
                templateId,
                normalizeFamily(family),
                normalizeLocale(locale),
                subjectTemplate,
                textTemplate,
                checkedHtmlTemplate(subjectTemplate, textTemplate, htmlTemplate),
                version);
        log.info("邮件模板更新完成 | templateId={} version={}", templateId, version + 1);
    }

    @Override
    public void changeStatus(
            @NotNull Long templateId, @NotNull MailTemplateStatus status, long version) {
        MailTemplate template = require(templateId);
        if (!template.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.TEMPLATE_CHANGED, MailClientStateTargets.TEMPLATES);
        }
        if (template.getStatus() == status) {
            return;
        }
        repository.updateStatus(templateId, template.getStatus(), status, version);
        log.info("邮件模板状态更新完成 | templateId={} status={} version={}",
                templateId, status, version + 1);
    }

    @Override
    public void delete(@NotNull Long templateId, long version) {
        MailTemplate template = require(templateId);
        if (!template.getVersion().equals(version)) {
            throw new ConcurrencyConflictException(
                    MailErrorCode.TEMPLATE_CHANGED, MailClientStateTargets.TEMPLATES);
        }
        if (template.getStatus() == MailTemplateStatus.ACTIVE) {
            throw new BizException(MailErrorCode.TEMPLATE_ACTIVE);
        }
        repository.deleteTemplate(templateId, version);
        log.info("邮件模板删除完成 | templateId={}", templateId);
    }

    @Override
    public @NotNull RenderedMail preview(
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate,
            @NotNull Map<String, String> variables) {
        return renderer.render(subjectTemplate, textTemplate, htmlTemplate, variables);
    }

    @Override
    public void sendTest(
            @NotNull Long templateId,
            @NotNull Long senderId,
            @NotNull String recipient,
            @NotNull Map<String, String> variables) {
        MailTemplate template = require(templateId);
        String checkedRecipient = recipient.trim();
        try {
            new InternetAddress(checkedRecipient, true).validate();
        } catch (AddressException exception) {
            throw new BizException(MailErrorCode.TEMPLATE_RECIPIENT_INVALID, exception);
        }
        RenderedMail mail = renderer.render(
                template.getSubjectTemplate(),
                template.getTextTemplate(),
                template.getHtmlTemplate(),
                variables);
        mailService.send(senderId, checkedRecipient, mail.getSubject(), mail.getText(), mail.getHtml());
        log.info("邮件模板测试发送完成 | templateId={} senderId={}", templateId, senderId);
    }

    @Override
    public @NotNull RenderedMail render(
            @NotNull String family,
            @NotNull String locale,
            @NotNull Map<String, String> variables) {
        MailTemplate template = repository.findActive(normalizeFamily(family), normalizeLocale(locale));
        if (template == null) {
            throw new BizException(MailErrorCode.ACTIVE_TEMPLATE_NOT_FOUND);
        }
        return renderer.render(
                template.getSubjectTemplate(),
                template.getTextTemplate(),
                template.getHtmlTemplate(),
                variables);
    }

    /**
     * 规范化模板族编码，并拒绝无法形成稳定目录键的值。
     *
     * @param value 调用方提交的模板族编码
     * @return 小写且符合模板族编码规则的值
     */
    private @NotNull String normalizeFamily(@NotNull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!FAMILY.matcher(normalized).matches()) {
            throw new BizException(MailErrorCode.TEMPLATE_FAMILY_INVALID);
        }
        return normalized;
    }

    /**
     * 使用 IETF BCP 47 规则规范化模板语言标签。
     *
     * @param value 调用方提交的语言标签
     * @return Java Locale 生成的标准语言标签
     */
    private @NotNull String normalizeLocale(@NotNull String value) {
        String candidate = value.trim();
        if (!LOCALE.matcher(candidate).matches()) {
            throw new BizException(MailErrorCode.TEMPLATE_LOCALE_INVALID);
        }
        String normalized = Locale.forLanguageTag(candidate).toLanguageTag();
        if (normalized.isEmpty() || "und".equals(normalized)) {
            throw new BizException(MailErrorCode.TEMPLATE_LOCALE_INVALID);
        }
        return normalized;
    }

    /**
     * 校验主题、纯文本和可选 HTML 模板，并把空白 HTML 统一为缺失值。
     *
     * @param subjectTemplate 邮件主题模板
     * @param textTemplate 纯文本正文模板
     * @param htmlTemplate 可选 HTML 正文模板
     * @return 经过语法校验的 HTML 模板；未提供有效文本时返回 null
     */
    private @Nullable String checkedHtmlTemplate(
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate) {
        if (!StringUtils.hasText(subjectTemplate) || !StringUtils.hasText(textTemplate)) {
            throw new BizException(MailErrorCode.TEMPLATE_CONTENT_INVALID);
        }
        if (subjectTemplate.indexOf('\r') >= 0 || subjectTemplate.indexOf('\n') >= 0) {
            throw new BizException(MailErrorCode.TEMPLATE_CONTENT_INVALID);
        }
        renderer.variables(subjectTemplate);
        renderer.variables(textTemplate);
        String normalizedHtml = StringUtils.hasText(htmlTemplate) ? htmlTemplate : null;
        if (normalizedHtml != null) {
            renderer.variables(normalizedHtml);
        }
        return normalizedHtml;
    }
}
