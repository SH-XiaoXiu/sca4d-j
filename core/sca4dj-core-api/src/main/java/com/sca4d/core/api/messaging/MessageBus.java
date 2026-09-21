package com.sca4d.core.api.messaging;

import com.sca4d.core.api.message.Message;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 把业务 Message 按指定策略交给固定 RabbitMQ 消息总线。 */
public interface MessageBus {

    /**
     * 分发消息。
     *
     * @return 当前调用已经完成的 Broker、事务回调或 Outbox 登记结果
     */
    @CheckReturnValue
    @NotNull
    DispatchReceipt dispatch(@NotNull Message message, @NotNull DispatchPolicy policy);
}
