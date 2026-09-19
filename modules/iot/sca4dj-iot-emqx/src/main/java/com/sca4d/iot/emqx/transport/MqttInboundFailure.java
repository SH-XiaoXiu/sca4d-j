package com.sca4d.iot.emqx.transport;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 MQTT 上行达到处理次数上限后的原始消息、处理次数和异常类型。 */
public final class MqttInboundFailure {

    private final @NotNull String deliveryId;

    private final @NotNull MqttMessage message;

    private final int attempts;

    private final @NotNull Class<? extends RuntimeException> failureType;

    private final @NotNull Class<? extends Throwable> rootCauseType;

    /**
     * 创建一条等待可靠保存的 MQTT 上行失败。
     *
     * @param deliveryId 当前进程为本次 Broker 交付生成的关联标识
     * @param message 达到处理上限的原始消息
     * @param attempts 已经执行的处理次数
     * @param failure 最后一次处理抛出的异常；只保存异常类型和根因类型
     * @throws IllegalArgumentException attempts 不是正数时抛出
     */
    public MqttInboundFailure(
            @NotNull String deliveryId,
            @NotNull MqttMessage message,
            int attempts,
            @NotNull RuntimeException failure) {
        if (attempts < 1) {
            throw new IllegalArgumentException("MQTT 上行处理次数必须为正数");
        }
        this.deliveryId = Objects.requireNonNull(deliveryId, "MQTT 交付标识不能为空");
        this.message = Objects.requireNonNull(message, "MQTT 上行消息不能为空");
        this.attempts = attempts;
        RuntimeException checkedFailure = Objects.requireNonNull(failure, "MQTT 上行失败不能为空");
        Throwable rootCause = checkedFailure;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        this.failureType = checkedFailure.getClass();
        this.rootCauseType = rootCause.getClass();
    }

    /** @return 当前进程为本次 Broker 交付生成的关联标识 */
    @Contract(pure = true)
    public @NotNull String deliveryId() {
        return deliveryId;
    }

    /** @return 达到处理上限的原始 MQTT 上行消息 */
    @Contract(pure = true)
    public @NotNull MqttMessage message() {
        return message;
    }

    /** @return 本次 Broker 交付已经执行的处理次数 */
    @Contract(pure = true)
    public int attempts() {
        return attempts;
    }

    /** @return 最后一次处理抛出的运行异常类型 */
    @Contract(pure = true)
    public @NotNull Class<? extends RuntimeException> failureType() {
        return failureType;
    }

    /** @return 最后一次处理异常的最内层根因类型 */
    @Contract(pure = true)
    public @NotNull Class<? extends Throwable> rootCauseType() {
        return rootCauseType;
    }
}
