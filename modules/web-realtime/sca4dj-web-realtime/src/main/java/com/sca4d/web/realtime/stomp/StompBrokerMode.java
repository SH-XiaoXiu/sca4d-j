package com.sca4d.web.realtime.stomp;

/** 选择 Spring STOMP destination 由当前节点还是 RabbitMQ STOMP 插件代理。 */
public enum StompBrokerMode {
    /** 当前节点使用 Spring simple broker，跨节点事件仍由 Web Realtime 的 RabbitMQ 节点传输负责。 */
    NODE_ROUTED,

    /** Spring 把 broker destination 转发给 RabbitMQ STOMP 插件。 */
    BROKER_RELAY
}
