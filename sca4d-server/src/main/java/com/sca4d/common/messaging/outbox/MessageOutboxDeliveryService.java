package com.sca4d.common.messaging.outbox;

/**
 * 领取到期的 Outbox 消息，发送到 RabbitMQ，并更新发送状态。
 */
public interface MessageOutboxDeliveryService {

    /**
     * 领取并投递当前到期的消息批次。
     *
     * <p>单条消息失败只进入自身重试或死信状态，不阻止同批其他消息继续投递。
     */
    void dispatchDueMessages();
}
