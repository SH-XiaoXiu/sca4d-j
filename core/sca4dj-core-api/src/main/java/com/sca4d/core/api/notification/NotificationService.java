package com.sca4d.core.api.notification;

import com.sca4d.core.api.messaging.MessageId;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 提供渠道无关的通知可用性、同步投递和可靠异步入队操作。 */
public interface NotificationService {

    /**
     * 判断当前配置能否处理指定通知和接收人。
     *
     * @param code 通知编码
     * @param recipient 内部接收人
     * @return 存在可用投递配置时为 {@code true}
     */
    @CheckReturnValue
    boolean isAvailable(@NotNull NotificationCode code, @NotNull NotificationRecipient recipient);

    /**
     * 同步投递通知。
     *
     * @param notification 完整通知
     * @throws RuntimeException 具体提供方未接受通知时抛出
     */
    void sendNow(@NotNull Notification notification);

    /**
     * 在当前事务中或新事务中把通知可靠写入 Messaging Outbox。
     *
     * @param notification 完整通知
     * @return Outbox 记录和最终代理消息共用的 MessageId；不表示通知已经投递
     */
    @CheckReturnValue
    @NotNull
    MessageId enqueue(@NotNull Notification notification);
}
