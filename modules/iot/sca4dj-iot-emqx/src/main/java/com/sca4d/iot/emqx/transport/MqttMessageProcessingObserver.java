package com.sca4d.iot.emqx.transport;

import com.sca4d.iot.emqx.transport.exception.MqttMessageRejectedException;
import org.jetbrains.annotations.NotNull;

/** 观察 MQTT 上行的拒绝、再次处理和最终失败，不参与消息确认决定。 */
public interface MqttMessageProcessingObserver {

    /**
     * 观察一条不会再次处理的格式、身份或协议拒绝结果。
     *
     * @param deliveryId 当前进程为本次 Broker 交付生成的关联标识
     * @param message 被拒绝的原始消息
     * @param failure 原因分类和原始校验异常
     */
    default void messageRejected(
            @NotNull String deliveryId,
            @NotNull MqttMessage message,
            @NotNull MqttMessageRejectedException failure) {
    }

    /**
     * 观察当前失败后即将执行下一次处理的结果。
     *
     * @param deliveryId 当前进程为本次 Broker 交付生成的关联标识
     * @param message 等待再次处理的原始消息
     * @param attempt 已经失败的处理次数
     * @param maximumAttempts 当前消息最多允许执行的处理次数
     * @param failure 本次处理抛出的异常
     */
    default void messageRetrying(
            @NotNull String deliveryId,
            @NotNull MqttMessage message,
            int attempt,
            int maximumAttempts,
            @NotNull RuntimeException failure) {
    }

    /**
     * 观察已经由可靠失败存储保存并允许确认的消息。
     *
     * @param failure 已经保存的原始消息、次数和最终异常类型
     */
    default void messageQuarantined(@SuppressWarnings("unused") @NotNull MqttInboundFailure failure) {
    }

    /**
     * 观察无法可靠保存而保持未确认的最终失败消息。
     *
     * @param failure 未保存的原始消息、次数和最终异常类型
     */
    default void messageUnacknowledged(@SuppressWarnings("unused") @NotNull MqttInboundFailure failure) {
    }
}
