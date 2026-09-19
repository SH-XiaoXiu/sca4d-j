package com.sca4d.iot.api.message;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示标准设备下行消息的持久化生命周期。 */
public enum DeviceDownlinkStatus implements BaseEnum {
    /** 已创建并等待 Device 可发送。 */
    PENDING("等待发送"),
    /** 当前消息 Consumer 正在调用协议 Publisher。 */
    SENDING("正在发送"),
    /** Transport 已确认发送，等待业务 ACK 或 reported 收敛。 */
    WAITING_ACK("等待确认"),
    /** 业务策略已经确认成功。 */
    SUCCEEDED("成功"),
    /** 策略确认失败或重试已经停止。 */
    FAILED("失败"),
    /** 同一 desired 属性已经产生更新的 desired sequence。 */
    SUPERSEDED("已被更新取代"),
    /** 调用方明确取消尚未完成的消息。 */
    CANCELLED("已取消"),
    /** 消息在首次成功发布前超过最晚发送时间。 */
    EXPIRED("已过期");

    private final @NotNull String description;

    DeviceDownlinkStatus(@NotNull String description) {
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return description;
    }

    /** @return 当前状态不会再发送、等待 ACK 或重试时为 `true` */
    public boolean terminal() {
        return this == SUCCEEDED
                || this == FAILED
                || this == SUPERSEDED
                || this == CANCELLED
                || this == EXPIRED;
    }
}
