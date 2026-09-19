package com.sca4d.notification.template.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.notification.NotificationCode;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读写统一 Notification 模板的最小 SQL 形状。 */
public interface NotificationTemplateMapper {

    /** 按可选标识、通知编码和启用状态读取未删除模板。 */
    @CheckReturnValue
    @NotNull
    List<@NotNull NotificationTemplateState> select(
            @Param("templateId") @Nullable Long templateId,
            @Param("notificationCode") @Nullable NotificationCode notificationCode,
            @Param("enabled") @Nullable Boolean enabled);

    /** @return 新增默认停用模板的行数 */
    int insert(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull NotificationCode notificationCode,
            @Param("contentJson") @NotNull String contentJson);

    /** @return 按预期版本完整替换模板的行数 */
    int replace(
            @Param("templateId") long templateId,
            @Param("notificationCode") @NotNull NotificationCode notificationCode,
            @Param("contentJson") @NotNull String contentJson,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /** @return 按预期版本修改启用状态的行数 */
    int setEnabled(
            @Param("templateId") long templateId,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /** @return 按预期版本逻辑删除停用模板的行数 */
    int markDeleted(
            @Param("templateId") long templateId,
            @Param("expectedVersion") @NotNull Version expectedVersion);
}
