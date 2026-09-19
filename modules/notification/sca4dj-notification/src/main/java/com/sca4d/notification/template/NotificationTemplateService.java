package com.sca4d.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.Page;
import com.sca4d.notification.NotificationErrorCode;
import com.sca4d.notification.NotificationTypeCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 管理平台无关 Tiptap JSON 通知模板，并提供统一预览结果。 */
public class NotificationTemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationTemplateService.class);

    private final NotificationTemplateRepository templates;

    private final NotificationTemplateRenderers renderers;

    private final NotificationTypeCatalog types;

    private final IdGenerator ids;

    /** 创建统一通知模板 Service。 */
    public NotificationTemplateService(
            @NotNull NotificationTemplateRepository templates,
            @NotNull NotificationTemplateRenderers renderers,
            @NotNull NotificationTypeCatalog types,
            @NotNull IdGenerator ids) {
        this.templates = Objects.requireNonNull(templates, "通知模板 Repository 不能为空");
        this.renderers = Objects.requireNonNull(renderers, "通知模板渲染器不能为空");
        this.types = Objects.requireNonNull(types, "通知类型目录不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
    }

    /** @return 按分页和排序要求读取的统一通知模板 */
    @Transactional(readOnly = true)
    public @NotNull Page<NotificationTemplate> find(@NotNull ListRequest request) {
        return templates.page(request);
    }

    @Transactional(readOnly = true)
    public @NotNull NotificationTemplate loadById(long templateId) {
        return templates.find(templateId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND));
    }

    @Transactional
    @CheckReturnValue
    public @NotNull NotificationTemplate create(@NotNull NotificationCode code, @NotNull JsonNode content) {
        validate(code, content);
        long templateId = ids.nextId();
        templates.add(templateId, code, renderers.validate(content));
        LOG.info("统一通知模板已创建 | templateId={} notificationCode={} enabled=false", templateId, code);
        return loadById(templateId);
    }

    @Transactional
    @CheckReturnValue
    public @NotNull NotificationTemplate replace(
            long templateId,
            @NotNull NotificationCode code,
            @NotNull JsonNode content) {
        validate(code, content);
        templates.replace(templateId, code, renderers.validate(content));
        LOG.info("统一通知模板已修改 | templateId={} notificationCode={}", templateId, code);
        return loadById(templateId);
    }

    @Transactional
    @CheckReturnValue
    public @NotNull NotificationTemplate setEnabled(long templateId, boolean enabled) {
        templates.setEnabled(templateId, enabled);
        LOG.info("统一通知模板状态已修改 | templateId={} enabled={}", templateId, enabled);
        return loadById(templateId);
    }

    @Transactional
    public void delete(long templateId) {
        NotificationTemplate template = loadById(templateId);
        if (template.enabled()) {
            throw new BusinessException(NotificationErrorCode.TEMPLATE_CONFLICT);
        }
        templates.delete(templateId);
        LOG.info("统一通知模板已删除 | templateId={}", templateId);
    }

    @CheckReturnValue
    public @NotNull RenderedNotification preview(
            @NotNull JsonNode content,
            @NotNull Locale locale,
            @NotNull Map<String, String> variables) {
        return renderers.renderDefaults(content, locale, variables);
    }

    /** @return 模板全部 Locale 使用且必须由 NotificationType 声明的变量 */
    @CheckReturnValue
    public @NotNull Set<@NotNull String> variables(@NotNull JsonNode content) {
        return renderers.variables(content);
    }

    /** 要求模板只引用通知类型声明的变量。 */
    private void validate(@NotNull NotificationCode code, @NotNull JsonNode content) {
        var type = types.types()
                .stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.TEMPLATE_INVALID));
        Set<String> used = renderers.variables(content);
        if (!type.definition().variables().keySet().containsAll(used)) {
            throw new BusinessException(NotificationErrorCode.TEMPLATE_INVALID);
        }
    }
}
