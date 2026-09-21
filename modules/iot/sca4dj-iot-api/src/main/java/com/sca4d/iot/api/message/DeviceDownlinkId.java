package com.sca4d.iot.api.message;

import com.sca4d.core.api.id.Id;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 标识一条已经持久化、会经过重试和设备业务 ACK 的下行消息。 */
public final class DeviceDownlinkId extends Id {

    /**
     * 创建下行消息标识。
     *
     * @param value `iot_downlink_message.id` 保存的正整数
     */
    public DeviceDownlinkId(long value) {
        super(value);
    }

    /** 使用已经校验的通用标识创建下行消息标识。 */
    public DeviceDownlinkId(@NotNull Id value) {
        super(Objects.requireNonNull(value, "下行消息标识不能为空").longValue());
    }
}
