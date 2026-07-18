package com.sca4d.mail.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.SysMailTemplateEntity;
import com.sca4d.mail.MailClientStateTargets;
import com.sca4d.mail.MailErrorCode;
import com.sca4d.mail.MailTemplate;
import com.sca4d.mail.MailTemplateStatus;
import com.sca4d.mapper.MailTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/** 读写邮件模板 family、locale、内容、状态和乐观锁版本。 */
@Repository
@RequiredArgsConstructor
public class MailTemplateRepository {

    private static final String FAMILY_LOCALE_CONSTRAINT = "uk_sys_mail_template_family_locale";

    private final MailTemplateMapper mapper;

    /** @return 全部未删除模板，按 family、locale 和主键排列 */
    public @NotNull List<MailTemplate> findTemplates() {
        QueryWrapper query = QueryWrapper.create()
                .where(SysMailTemplateEntity::getDeleted)
                .eq(0)
                .orderBy(SysMailTemplateEntity::getFamily)
                .asc()
                .orderBy(SysMailTemplateEntity::getLocale)
                .asc()
                .orderBy(SysMailTemplateEntity::getId)
                .asc();
        return mapper.selectListByQuery(query).stream().map(this::template).toList();
    }

    /** @return 指定未删除模板；不存在时返回 {@code null} */
    public @Nullable MailTemplate findTemplate(@NotNull Long templateId) {
        QueryWrapper query = QueryWrapper.create()
                .where(SysMailTemplateEntity::getId)
                .eq(templateId)
                .and(SysMailTemplateEntity::getDeleted)
                .eq(0);
        SysMailTemplateEntity entity = mapper.selectOneByQuery(query);
        return entity == null ? null : template(entity);
    }

    /** @return 指定 family 和 locale 下启用的模板；不存在时返回 {@code null} */
    public @Nullable MailTemplate findActive(
            @NotNull String family, @NotNull String locale) {
        QueryWrapper query = QueryWrapper.create()
                .where(SysMailTemplateEntity::getFamily)
                .eq(family)
                .and(SysMailTemplateEntity::getLocale)
                .eq(locale)
                .and(SysMailTemplateEntity::getStatus)
                .eq(MailTemplateStatus.ACTIVE)
                .and(SysMailTemplateEntity::getDeleted)
                .eq(0);
        SysMailTemplateEntity entity = mapper.selectOneByQuery(query);
        return entity == null ? null : template(entity);
    }

    /** 批量读取操作日志显示需要的 family、locale 和删除状态。 */
    public @NotNull List<SysMailTemplateEntity> findTemplateReferences(
            @NotNull Set<Long> templateIds) {
        if (templateIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysMailTemplateEntity::getId),
                        column(SysMailTemplateEntity::getFamily),
                        column(SysMailTemplateEntity::getLocale),
                        column(SysMailTemplateEntity::getDeleted))
                .where(SysMailTemplateEntity::getId)
                .in(templateIds);
        return mapper.selectListByQuery(query);
    }

    /** 写入一项已经完成语法校验、默认停用的模板。 */
    public void createTemplate(
            @NotNull Long templateId,
            @NotNull String family,
            @NotNull String locale,
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate) {
        SysMailTemplateEntity entity = new SysMailTemplateEntity()
                .setId(templateId)
                .setFamily(family)
                .setLocale(locale)
                .setSubjectTemplate(subjectTemplate)
                .setTextTemplate(textTemplate)
                .setHtmlTemplate(htmlTemplate)
                .setStatus(MailTemplateStatus.DISABLED)
                .setDeleted(0);
        try {
            if (mapper.insertTemplate(entity) != 1) {
                throw new IllegalStateException("邮件模板写入行数异常");
            }
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", FAMILY_LOCALE_CONSTRAINT)) {
                throw new BizException(MailErrorCode.TEMPLATE_EXISTS, exception);
            }
            throw exception;
        }
    }

    /** 按主键和版本原子修改模板标识及内容。 */
    public void updateTemplate(
            @NotNull Long templateId,
            @NotNull String family,
            @NotNull String locale,
            @NotNull String subjectTemplate,
            @NotNull String textTemplate,
            @Nullable String htmlTemplate,
            long version) {
        try {
            if (mapper.updateTemplate(
                    templateId,
                    family,
                    locale,
                    subjectTemplate,
                    textTemplate,
                    htmlTemplate,
                    version) != 1) {
                throw changed();
            }
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", FAMILY_LOCALE_CONSTRAINT)) {
                throw new BizException(MailErrorCode.TEMPLATE_EXISTS, exception);
            }
            throw exception;
        }
    }

    /** 按当前状态和版本原子推进模板状态。 */
    public void updateStatus(
            @NotNull Long templateId,
            @NotNull MailTemplateStatus expectedStatus,
            @NotNull MailTemplateStatus nextStatus,
            long version) {
        if (mapper.updateStatus(templateId, expectedStatus, nextStatus, version) != 1) {
            throw changed();
        }
    }

    /** 在版本匹配时把停用模板推进到逻辑删除状态。 */
    public void deleteTemplate(@NotNull Long templateId, long version) {
        if (mapper.markDeleted(templateId, version) != 1) {
            throw changed();
        }
    }

    private @NotNull MailTemplate template(@NotNull SysMailTemplateEntity entity) {
        return new MailTemplate(
                entity.getId(),
                entity.getFamily(),
                entity.getLocale(),
                entity.getSubjectTemplate(),
                entity.getTextTemplate(),
                entity.getHtmlTemplate(),
                entity.getStatus(),
                entity.getVersion());
    }

    private @NotNull ConcurrencyConflictException changed() {
        return new ConcurrencyConflictException(
                MailErrorCode.TEMPLATE_CHANGED, MailClientStateTargets.TEMPLATES);
    }
}
