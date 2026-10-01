package com.sca4d.core.api.flow;

/** 指定 {@link FlowGraph} 节点接收事件的方式。 */
public enum FlowNodeType {
    /** 等待调用方完成一个明确步骤并提交事件。 */
    STEP,
    /** 等待外部系统或下一次请求提交事件。 */
    WAITING,
    /** 实例到达终点，只能等待单次消费。 */
    TERMINAL
}
