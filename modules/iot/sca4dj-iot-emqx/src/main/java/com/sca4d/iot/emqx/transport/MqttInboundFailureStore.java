package com.sca4d.iot.emqx.transport;

import org.jetbrains.annotations.NotNull;

/** 在 MQTT Runtime 确认失败消息前可靠保存原始消息和最终失败。 */
@FunctionalInterface
public interface MqttInboundFailureStore {

    /**
     * 可靠保存一条达到处理次数上限的 MQTT 消息。
     *
     * <p>正常返回表示事务已经提交或外部存储已经确认，MQTT Runtime 随后可以 ACK。抛出异常时 Runtime
     * 保留消息的未确认状态，不能把日志或进程内状态当作可靠保存。
     *
     * @param failure 原始消息、交付标识、处理次数和最终失败
     */
    void store(@NotNull MqttInboundFailure failure);
}
