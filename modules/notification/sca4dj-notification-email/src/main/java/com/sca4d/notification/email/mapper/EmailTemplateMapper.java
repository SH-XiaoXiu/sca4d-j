package com.sca4d.notification.email.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.SortOrder;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 读写 Email 模板、启用状态和版本条件。
 */
public interface EmailTemplateMapper {
    /** @return 匹配条件的未删除 Email 模板状态 */
    @NotNull
    List<@NotNull EmailTemplateState> select(
            @Param("templateId") @Nullable Long templateId,
            @Param("notificationCode") @Nullable TreeCode notificationCode,
            @Param("enabled") @Nullable Boolean enabled);

    /**
     * @return 未删除 Email 模板总数
     */
    long count();

    /** @return 按分页条件读取的 Email 模板状态 */
    @NotNull
    List<@NotNull EmailTemplateState> selectPage(
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("sort") @NotNull List<@NotNull SortOrder> sort);

    /**
     * @return 新增 Email 模板的行数
     */
    int insert(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull TreeCode notificationCode,
            @Param("localesJson") @NotNull String localesJson);

    /**
     * @return 按预期版本修改指定字段的行数
     */
    int update(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull Patch<TreeCode> notificationCode,
            @Param("localesJson") @NotNull Patch<String> localesJson,
            @Param("enabled") @NotNull Patch<Boolean> enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * @return 按预期版本逻辑删除模板的行数
     */
    int markDeleted(@Param("templateId") long templateId, @Param("expectedVersion") @NotNull Version expectedVersion);
}
