package com.sca4d.core.api.notification;

import com.sca4d.core.api.id.Id;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 发布类型化领域通知，并隐藏 Settings、模板变量和渠道路由。 */
@SuppressWarnings("unused")
public interface Notifications {

    /** 判断通知类型声明的必发渠道当前能否处理指定接收人。 */
    @CheckReturnValue
    boolean isAvailable(@NotNull NotificationType<?> type, @NotNull NotificationRecipient recipient);

    /** 同步解析并投递领域通知；全部选中渠道返回后才完成。 */
    <T> void sendNow(@NotNull NotificationType<T> type, @NotNull T value);

    /** 在当前事务中把解析后的通知可靠写入 Messaging Outbox。 */
    @CheckReturnValue
    <T> @NotNull Id enqueue(@NotNull NotificationType<T> type, @NotNull T value);
}
