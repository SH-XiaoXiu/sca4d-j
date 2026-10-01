package com.sca4d.web.realtime.config;

/** 选择 Web Realtime 在单个 JVM 内投递，或通过 RabbitMQ 支持跨节点投递。 */
public enum NodeRouterMode {

    /** 只投递当前 JVM 持有的连接，适用于单实例运行。 */
    LOCAL,

    /** 当前 JVM 直接投递本机连接，并通过 RabbitMQ 临时队列投递其他节点的连接。 */
    RABBIT
}
