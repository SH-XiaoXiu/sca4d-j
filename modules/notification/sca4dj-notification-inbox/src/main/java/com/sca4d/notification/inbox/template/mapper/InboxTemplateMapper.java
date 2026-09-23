package com.sca4d.notification.inbox.template.mapper;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.SortOrder;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 读写站内信模板，并按版本条件执行修改和逻辑删除。
 */
public interface InboxTemplateMapper {

    /**
     * 按可选标识、通知编码和启用状态读取未删除模板。
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull InboxTemplateState> select(
            @Param("templateId") @Nullable Long templateId,
            @Param("notificationCode") @Nullable TreeCode notificationCode,
            @Param("enabled") @Nullable Boolean enabled);

    /**
     * @return 未删除模板总数
     */
    long count();

    /**
     * @return 按分页和安全排序要求读取的模板
     */
    @NotNull
    List<@NotNull InboxTemplateState> selectPage(
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("sort") @NotNull List<@NotNull SortOrder> sort);

    /**
     * @return 新增默认停用模板的行数
     */
    int insert(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull TreeCode notificationCode,
            @Param("contentJson") @NotNull String contentJson);

    /**
     * @return 按预期版本修改明确指定字段的行数
     */
    int update(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull Patch<TreeCode> notificationCode,
            @Param("contentJson") @NotNull Patch<String> contentJson,
            @Param("enabled") @NotNull Patch<Boolean> enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /**
     * @return 按预期版本逻辑删除停用模板的行数
     */
    int markDeleted(
            @Param("templateId") long templateId,
            @Param("expectedVersion") @NotNull Version expectedVersion);
}
