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
    /** 执行 当前对象 方法表示的业务操作。 */
    @NotNull
    List<@NotNull EmailTemplateState> select(
            @Param("templateId") @Nullable Long templateId,
            @Param("notificationCode") @Nullable NotificationCode notificationCode,
            @Param("enabled") @Nullable Boolean enabled);

    /** 执行 count 方法表示的业务操作。 */
    long count();

    /** 执行 当前对象 方法表示的业务操作。 */
    @NotNull
    List<@NotNull EmailTemplateState> selectPage(
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("sort") @NotNull List<@NotNull SortOrder> sort);

    /** 执行 insert 方法表示的业务操作。 */
    int insert(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull NotificationCode notificationCode,
            @Param("localesJson") @NotNull String localesJson);

    /** 执行 update 方法表示的业务操作。 */
    int update(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull Patch<NotificationCode> notificationCode,
            @Param("localesJson") @NotNull Patch<String> localesJson,
            @Param("enabled") @NotNull Patch<Boolean> enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /** 执行 markDeleted 方法表示的业务操作。 */
    int markDeleted(@Param("templateId") long templateId, @Param("expectedVersion") @NotNull Version expectedVersion);
}
