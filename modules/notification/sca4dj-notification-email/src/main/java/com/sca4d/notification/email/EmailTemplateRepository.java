package com.sca4d.notification.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.api.query.SortOrder;
import com.sca4d.notification.email.mapper.EmailTemplateMapper;
import com.sca4d.notification.email.mapper.EmailTemplateState;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Email 模块拥有的模板持久化边界。
 */
public class EmailTemplateRepository {
    private static final Sort NOTIFICATION_CODE = Sort.of("NOTIFICATION_CODE");

    private static final Set<Sort> SORT_FIELDS = Set.of(NOTIFICATION_CODE, Sort.SERIAL);

    private final @NotNull EmailTemplateMapper mapper;

    private final @NotNull VersionConditions versions;

    private final @NotNull ObjectMapper json;

    /**
     * @param mapper Email 模板 Mapper
     * @param versions 版本条件解析器
     * @param json 模板 JSON 编解码器
     */
    public EmailTemplateRepository(
            @NotNull EmailTemplateMapper mapper,
            @NotNull VersionConditions versions,
            @NotNull ObjectMapper json) {
        this.mapper = Objects.requireNonNull(mapper, "EmailTemplateMapper 不能为空");
        this.versions = Objects.requireNonNull(versions, "版本条件不能为空");
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
    }

    /**
     * 读取指定通知编码当前启用的第一份 Email 模板；没有启用模板时返回空。
     */
    @CheckReturnValue
    public @NotNull Optional<EmailTemplate> findEnabled(@NotNull TreeCode code) {
        return mapper.select(null, Objects.requireNonNull(code, "通知编码不能为空"), true).stream().findFirst().map(this::map);
    }

    /**
     * 按通知编码读取未删除模板，无论模板当前是否启用。
     */
    @CheckReturnValue
    public @NotNull Optional<EmailTemplate> findByCode(@NotNull TreeCode code) {
        return mapper.select(null, Objects.requireNonNull(code, "通知编码不能为空"), null).stream().findFirst().map(this::map);
    }

    /**
     * 按模板标识读取 Email 模板；模板不存在或已经逻辑删除时返回空。
     */
    @CheckReturnValue
    public @NotNull Optional<EmailTemplate> find(long templateId) {
        return mapper.select(templateId, null, null).stream().findFirst().map(this::map);
    }

    /**
     * 分页读取未删除 Email 模板，并验证排序字段和最大页大小。
     */
    @CheckReturnValue
    public @NotNull Page<EmailTemplate> page(@NotNull ListRequest request) {
        ListRequest checked = Objects.requireNonNull(request, "Email 模板列表要求不能为空");
        var page = checked.getPage().verifyMaxSize(100);
        for (SortOrder order : checked.getSort()) {
            if (!SORT_FIELDS.contains(order.getField())) {
                throw new IllegalArgumentException("Email 模板列表包含不支持的排序字段: " + order.getField());
            }
        }
        long total = mapper.count();
        List<EmailTemplate> items = total == 0
                ? List.of()
                : mapper.selectPage(page.offset(), page.size(), checked.getSort())
                        .stream()
                        .map(this::map)
                        .toList();
        return new Page<>(items, page, total);
    }

    /**
     * 新增 Email 模板；通知编码冲突时抛出模板冲突异常。
     */
    public void add(
            long templateId,
            @NotNull TreeCode code,
            @NotNull Map<String, EmailTemplateSource> locales) {
        try {
            mapper.insert(templateId, Objects.requireNonNull(code, "通知编码不能为空"), write(locales));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_CONFLICT, exception);
        }
    }

    /**
     * 按当前版本替换 Email 模板内容；版本冲突或通知编码冲突时失败。
     */
    public void replace(
            long templateId,
            @NotNull TreeCode code,
            @NotNull Map<String, EmailTemplateSource> locales) {
        EmailTemplate current = require(templateId);
        VersionCondition condition = versions.resolve(current.version());
        try {
            AffectedRowCounts.requireSingle(
                    mapper.update(
                            templateId,
                            Patch.set(code),
                            Patch.set(write(locales)),
                            Patch.ignore(),
                            condition.expected()),
                    condition.whenZero(),
                    "Email Template");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_CONFLICT, exception);
        }
    }

    /**
     * 按当前版本修改 Email 模板的启用状态。
     */
    public void setEnabled(long templateId, boolean enabled) {
        EmailTemplate current = require(templateId);
        VersionCondition condition = versions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.update(templateId, Patch.ignore(), Patch.ignore(), Patch.set(enabled), condition.expected()),
                condition.whenZero(),
                "Email Template");
    }

    /**
     * 按当前版本逻辑删除 Email 模板。
     */
    public void delete(long templateId) {
        EmailTemplate current = require(templateId);
        VersionCondition condition = versions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.markDeleted(templateId, condition.expected()),
                condition.whenZero(),
                "Email Template");
    }

    private @NotNull EmailTemplate require(long templateId) {
        return find(templateId).orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.TEMPLATE_NOT_FOUND));
    }

    private @NotNull EmailTemplate map(@NotNull EmailTemplateState state) {
        try {
            JsonNode root = json.readTree(state.localesJson());
            Map<String, EmailTemplateSource> locales = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                locales.put(
                        entry.getKey(),
                        new EmailTemplateSource(
                                value.path("subject").asText(),
                                value.path("text").asText(),
                                value.path("html").asText()));
            });
            return new EmailTemplate(state.templateId(), state.code(), locales, state.enabled(), state.version());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的 Email 模板 JSON 无法解析", exception);
        }
    }

    private @NotNull String write(@NotNull Map<String, EmailTemplateSource> locales) {
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            locales.forEach(
                    (locale, source) -> values.put(
                            locale,
                            Map.of("subject", source.subject(), "text", source.text(), "html", source.html())));
            return json.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Email 模板无法编码", exception);
        }
    }
}
