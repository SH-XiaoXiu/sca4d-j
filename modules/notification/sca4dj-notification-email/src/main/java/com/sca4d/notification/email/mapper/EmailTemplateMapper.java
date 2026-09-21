package com.sca4d.notification.email.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.SortOrder;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Email 模板表的最小 SQL 映射。 */
public interface EmailTemplateMapper {
    @NotNull
    List<@NotNull EmailTemplateState> select(
            @Param("templateId") @Nullable Long templateId,
            @Param("notificationCode") @Nullable NotificationCode notificationCode,
            @Param("enabled") @Nullable Boolean enabled);

    long count();

    @NotNull
    List<@NotNull EmailTemplateState> selectPage(
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("sort") @NotNull List<@NotNull SortOrder> sort);

    int insert(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull NotificationCode notificationCode,
            @Param("localesJson") @NotNull String localesJson);

    int update(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull Patch<NotificationCode> notificationCode,
            @Param("localesJson") @NotNull Patch<String> localesJson,
            @Param("enabled") @NotNull Patch<Boolean> enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    int markDeleted(@Param("templateId") long templateId, @Param("expectedVersion") @NotNull Version expectedVersion);
}
