package com.sca4d.core.api.notification;

import com.sca4d.core.api.messaging.MessageId;
import org.jetbrains.annotations.NotNull;

/** 由一个具体渠道实现通知可用性判断和一次幂等投递。 */
public interface NotificationDelivery {

    /** @return 当前实现负责的唯一通知渠道 */
    @NotNull
    NotificationChannel channel();

    /**
     * 判断当前配置能否处理指定通知编码和接收人。
     *
     * @param notification 已冻结接收人、Locale、区域和变量的通知
     * @return 当前配置存在且接收人可解析时为 {@code true}
     */
    boolean available(@NotNull Notification notification);

    /**
     * 同步投递通知，正常返回表示具体提供方已经接受。
     *
     * @param messageId 原始通知消息标识，渠道可以用它防止重复副作用
     * @param notification 已冻结接收人、Locale、区域、变量和渠道的通知
     * @throws RuntimeException 接收人、模板、配置、渲染或外部投递失败时抛出
     */
    void deliver(@NotNull MessageId messageId, @NotNull Notification notification);
}
