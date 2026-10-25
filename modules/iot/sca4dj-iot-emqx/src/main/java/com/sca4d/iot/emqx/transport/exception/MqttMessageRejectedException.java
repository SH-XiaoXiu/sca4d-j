package com.sca4d.iot.emqx.transport.exception;

import com.sca4d.iot.emqx.transport.MqttMessageRejectionReason;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示 MQTT 上行存在确定的输入错误，记录后应确认消息且不再执行同一业务动作。 */
public class MqttMessageRejectedException extends RuntimeException {

    private final @NotNull MqttMessageRejectionReason reason;

    /**
     * 创建不会因再次处理恢复的 MQTT 上行错误。
     *
     * @param reason 稳定拒绝原因
     * @param message 不包含完整 Payload 和凭据的错误说明
     * @param cause 原始 Topic、身份或协议校验异常
     */
    public MqttMessageRejectedException(
            @NotNull MqttMessageRejectionReason reason,
            @NotNull String message,
            @NotNull RuntimeException cause) {
        super(
                Objects.requireNonNull(message, "MQTT 上行拒绝说明不能为空"),
                Objects.requireNonNull(cause, "MQTT 上行拒绝异常不能为空"));
        this.reason = Objects.requireNonNull(reason, "MQTT 上行拒绝原因不能为空");
    }

    /** @return 当前消息不会因再次处理恢复的原因分类 */
    @Contract(pure = true)
    public @NotNull MqttMessageRejectionReason reason() {
        return reason;
    }
}
