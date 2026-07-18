package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.SysMailTemplateEntity;
import com.sca4d.mail.MailTemplateStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 邮件模板创建、乐观更新、状态修改和逻辑删除 SQL Mapper。 */
@Mapper
public interface MailTemplateMapper extends BaseMapper<SysMailTemplateEntity> {

    /** @return 实际写入行数，只能为 1 */
    int insertTemplate(@Param("template") @NotNull SysMailTemplateEntity template);

    /** @return 主键和版本匹配时更新 1 行，否则为 0 */
    int updateTemplate(
            @Param("templateId") @NotNull Long templateId,
            @Param("family") @NotNull String family,
            @Param("locale") @NotNull String locale,
            @Param("subjectTemplate") @NotNull String subjectTemplate,
            @Param("textTemplate") @NotNull String textTemplate,
            @Param("htmlTemplate") @Nullable String htmlTemplate,
            @Param("version") long version);

    /** @return 当前状态和版本同时匹配时更新 1 行，否则为 0 */
    int updateStatus(
            @Param("templateId") @NotNull Long templateId,
            @Param("expectedStatus") @NotNull MailTemplateStatus expectedStatus,
            @Param("nextStatus") @NotNull MailTemplateStatus nextStatus,
            @Param("version") long version);

    /** @return 主键和版本匹配时逻辑删除 1 行，否则为 0 */
    int markDeleted(@Param("templateId") @NotNull Long templateId, @Param("version") long version);
}
