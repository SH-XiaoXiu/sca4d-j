package com.sca4d.app.server.system;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.notification.NotificationType;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.render.TemplateVariables;
import com.sca4d.notification.NotificationTypeCatalog;
import com.sca4d.notification.email.EmailNotificationErrorCode;
import com.sca4d.notification.email.EmailRenderer;
import com.sca4d.notification.email.EmailTemplate;
import com.sca4d.notification.email.EmailTemplateRepository;
import com.sca4d.notification.email.EmailTemplateSource;
import com.sca4d.notification.email.RenderedEmail;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 管理 Email 模板源码、Locale、变量声明和安全预览。 */
@Service
public class EmailTemplateManagementService {

    private final @NotNull LocaleCatalog locales;

    private final @NotNull EmailTemplateRepository templates;

    private final @NotNull NotificationTypeCatalog types;

    private final @NotNull IdGenerator ids;

    private final @NotNull EmailRenderer renderer;

    public EmailTemplateManagementService(
            @NotNull LocaleCatalog locales,
            @NotNull EmailTemplateRepository templates,
            @NotNull NotificationTypeCatalog types,
            @NotNull IdGenerator ids,
            @NotNull EmailRenderer renderer) {
        this.locales = Objects.requireNonNull(locales, "LocaleCatalog 不能为空");
        this.templates = Objects.requireNonNull(templates, "Email 模板 Repository 不能为空");
        this.types = Objects.requireNonNull(types, "通知类型目录不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
        this.renderer = Objects.requireNonNull(renderer, "Email Renderer 不能为空");
    }

    @Transactional(readOnly = true)
    public @NotNull Page<EmailTemplate> find(@NotNull ListRequest request) {
        return templates.page(request);
    }

    @Transactional(readOnly = true)
    public @NotNull EmailTemplate loadById(long templateId) {
        return templates.find(templateId)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.TEMPLATE_NOT_FOUND));
    }

    @Transactional
    public @NotNull EmailTemplate create(
            @NotNull NotificationCode code,
            @NotNull Map<String, EmailTemplateSource> submitted) {
        Map<String, EmailTemplateSource> checked = validate(code, submitted);
        long templateId = ids.nextId().value();
        templates.add(templateId, code, checked);
        return loadById(templateId);
    }

    @Transactional
    public @NotNull EmailTemplate replace(
            long templateId,
            @NotNull NotificationCode code,
            @NotNull Map<String, EmailTemplateSource> submitted) {
        templates.replace(templateId, code, validate(code, submitted));
        return loadById(templateId);
    }

    @Transactional
    public @NotNull EmailTemplate setEnabled(long templateId, boolean enabled) {
        templates.setEnabled(templateId, enabled);
        return loadById(templateId);
    }

    @Transactional
    public void delete(long templateId) {
        EmailTemplate current = loadById(templateId);
        if (current.enabled()) {
            throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_CONFLICT);
        }
        templates.delete(templateId);
    }

    public @NotNull Set<String> variables(@NotNull Map<String, EmailTemplateSource> sources) {
        Set<String> result = new java.util.LinkedHashSet<>();
        sources.values().forEach(source -> result.addAll(renderer.inspect(source).variables()));
        return Set.copyOf(result);
    }

    public @NotNull RenderedEmail preview(
            @NotNull EmailTemplateSource source,
            @NotNull Locale locale,
            @NotNull Map<String, String> variables) {
        return renderer.render(
                source,
                locale,
                TemplateVariables.fillMissing(renderer.inspect(source).variables(), variables));
    }

    private @NotNull Map<String, EmailTemplateSource> validate(
            @NotNull NotificationCode code,
            @NotNull Map<String, EmailTemplateSource> submitted) {
        NotificationType<?> type = types.types()
                .stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.TEMPLATE_INVALID));
        Map<String, EmailTemplateSource> checked = new LinkedHashMap<>();
        for (Map.Entry<String, EmailTemplateSource> entry : Objects.requireNonNull(
                submitted,
                "Email 模板 Locale 不能为空").entrySet()) {
            Locale locale = Locale.forLanguageTag(entry.getKey());
            if (locale.getLanguage().isBlank() || !locales.supportedLocales().contains(locale)) {
                throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_INVALID);
            }
            checked.put(locale.toLanguageTag(), Objects.requireNonNull(entry.getValue(), "Email 模板内容不能为空"));
        }
        if (!checked.containsKey(locales.defaultLocale().toLanguageTag())) {
            throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_INVALID);
        }
        if (!type.definition().variables().keySet().containsAll(variables(checked))) {
            throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_INVALID);
        }
        return Map.copyOf(checked);
    }
}
