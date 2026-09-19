package com.sca4d.core.api.messaging;

import com.sca4d.core.api.event.EventType;
import org.jetbrains.annotations.NotNull;

/** 声明一个稳定的业务事件 Consumer 契约。 */
public interface MessageConsumer<T> {

    /** @return 在同一 App 内唯一且跨部署保持稳定的 Consumer 编码 */
    @NotNull
    String consumerCode();

    /** @return 当前 Consumer 接受的事件编码、协议版本和 payload 类型 */
    @NotNull
    EventType<T> eventType();

    /**
     * 消费一条 RabbitMQ 投递的业务消息。正常返回后 Messaging 才确认消息。
     *
     * @param delivery 已按当前 Consumer 契约反序列化的事件及 RabbitMQ 投递元数据
     * @throws Exception 业务处理失败时抛出；Messaging 会拒绝消息并送入当前 Consumer 的 DLQ
     */
    void consume(@NotNull MessageDelivery<T> delivery) throws Exception;
}
