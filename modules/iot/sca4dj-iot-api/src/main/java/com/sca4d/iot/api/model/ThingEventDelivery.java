package com.sca4d.iot.api.model;

/** 声明 ThingEvent 通过通用 `MessagePublisher` 发布时是否使用 Outbox。 */
public enum ThingEventDelivery {
    /** 主事务提交后尝试发布一次，不写入 Outbox。 */
    AFTER_COMMIT,

    /** 在主事务中写入通用 Messaging Outbox，直到消息代理确认发布。 */
    RELIABLE
}
