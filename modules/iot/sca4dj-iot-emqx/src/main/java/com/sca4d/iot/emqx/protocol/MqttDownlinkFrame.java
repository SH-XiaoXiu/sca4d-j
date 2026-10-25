package com.sca4d.iot.emqx.protocol;

import com.sca4d.core.api.data.DataValues;
import com.sca4d.iot.api.message.DeviceDownlink;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 把标准下行消息元数据和已按 `DataKey` 整理的值交给协议 Pipeline。 */
public class MqttDownlinkFrame {

    private final @NotNull DataValues values;

    private final @NotNull DeviceDownlink message;

    /**
     * 创建协议 Pipeline 的输入帧。
     *
     * @param message 标准下行消息，保留 messageId、目标 code 和发送尝试次数
     * @param values 已经按 `DataKey` 校验并整理的下行值
     */
    public MqttDownlinkFrame(
            @NotNull DeviceDownlink message,
            @NotNull DataValues values) {
        this.message = Objects.requireNonNull(message, "标准下行消息不能为空");
        this.values = Objects.requireNonNull(values, "下行 DataValues 不能为空");
    }

    /** @return 包含 messageId、目标 code 和发送尝试次数的标准下行消息 */
    public @NotNull DeviceDownlink message() {
        return message;
    }

    /** @return 已按物模型和 `DataKey` 校验的下行值 */
    public @NotNull DataValues values() {
        return values;
    }
}
