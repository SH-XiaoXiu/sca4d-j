package com.sca4d.notification.inbox.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.api.query.SortOrder;
import com.sca4d.notification.NotificationErrorCode;
import com.sca4d.notification.inbox.template.mapper.InboxTemplateMapper;
import com.sca4d.notification.inbox.template.mapper.InboxTemplateState;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 隐藏站内信模板的 JSONB、Mapper 和影响行数语义。 */
public class InboxTemplateRepository {

    private static final Sort NOTIFICATION_CODE = Sort.of("NOTIFICATION_CODE");

    private static final Set<Sort> SORT_FIELDS = Set.of(NOTIFICATION_CODE, Sort.SERIAL);

    private final @NotNull InboxTemplateMapper mapper;

    private final @NotNull VersionConditions versionConditions;

    private final @NotNull ObjectMapper objectMapper;

    /** 创建站内信模板 Repository。 */
    public InboxTemplateRepository(
            @NotNull InboxTemplateMapper mapper,
            @NotNull VersionConditions versionConditions,
            @NotNull ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "InboxTemplateMapper 不能为空");
        this.versionConditions = Objects.requireNonNull(versionConditions, "版本条件不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    /** @return 按安全排序要求读取的一页站内信模板和完整命中数量 */
    @CheckReturnValue
    public @NotNull Page<InboxTemplate> page(@NotNull ListRequest request) {
        ListRequest checked = Objects.requireNonNull(request, "站内信模板列表要求不能为空");
        var page = checked.getPage().verifyMaxSize(100);
        for (SortOrder order : checked.getSort()) {
            if (!SORT_FIELDS.contains(order.getField())) {
                throw new IllegalArgumentException("站内信模板列表包含不支持的排序字段: " + order.getField());
            }
        }
        long total = mapper.count();
        List<InboxTemplate> items = total == 0
                ? List.of()
                : mapper.selectPage(
                        checked.getPage().offset(),
                        page.size(),
                        checked.getSort())
                        .stream()
                        .map(this::template)
                        .toList();
        return new Page<>(items, page, total);
    }

    /** @return 指定模板；不存在时为空 */
    @CheckReturnValue
    public @NotNull Optional<InboxTemplate> find(long templateId) {
        return mapper.select(templateId, null, null).stream().findFirst().map(this::template);
    }

    /** @return 指定通知编码当前启用的统一模板 */
    @CheckReturnValue
    public @NotNull Optional<InboxTemplate> findEnabled(@NotNull NotificationCode code) {
        return mapper.select(null, Objects.requireNonNull(code, "通知编码不能为空"), true)
                .stream()
                .findFirst()
                .map(this::template);
    }

    /** 新增默认停用模板。 */
    @AffectedRows(target = "新增 Inbox Template")
    public void add(long templateId, @NotNull NotificationCode code, @NotNull JsonNode content) {
        try {
            mapper.insert(templateId, Objects.requireNonNull(code, "通知编码不能为空"), write(content));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(NotificationErrorCode.TEMPLATE_CONFLICT, exception);
        }
    }

    /** 按当前请求版本条件完整替换编码和模板内容。 */
    public void replace(long templateId, @NotNull NotificationCode code, @NotNull JsonNode content) {
        InboxTemplate current = require(templateId);
        VersionCondition condition = versionConditions.resolve(current.version());
        try {
            AffectedRowCounts.requireSingle(
                    mapper.update(
                            templateId,
                            Patch.set(code),
                            Patch.set(write(content)),
                            Patch.ignore(),
                            condition.expected()),
                    condition.whenZero(),
                    "Inbox Template");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(NotificationErrorCode.TEMPLATE_CONFLICT, exception);
        }
    }

    /** 按当前请求版本条件修改模板启用状态。 */
    public void setEnabled(long templateId, boolean enabled) {
        InboxTemplate current = require(templateId);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.update(
                        templateId,
                        Patch.ignore(),
                        Patch.ignore(),
                        Patch.set(enabled),
                        condition.expected()),
                condition.whenZero(),
                "Inbox Template");
    }

    /** 按当前请求版本条件删除停用模板。 */
    public void delete(long templateId) {
        InboxTemplate current = require(templateId);
        VersionCondition condition = versionConditions.resolve(current.version());
        AffectedRowCounts.requireSingle(
                mapper.markDeleted(templateId, condition.expected()),
                condition.whenZero(),
                "Inbox Template");
    }

    private @NotNull InboxTemplate require(long templateId) {
        return find(templateId).orElseThrow(() -> new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND));
    }

    private @NotNull InboxTemplate template(@NotNull InboxTemplateState state) {
        try {
            return new InboxTemplate(
                    state.templateId(),
                    state.code(),
                    objectMapper.readTree(state.contentJson()),
                    state.enabled(),
                    state.version());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的站内信模板 JSON 无法解析", exception);
        }
    }

    private @NotNull String write(@NotNull JsonNode content) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNull(content, "站内信模板内容不能为空"));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("站内信模板内容无法编码", exception);
        }
    }
}
