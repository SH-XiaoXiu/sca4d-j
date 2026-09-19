package com.sca4d.iot.api.protocol;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.Device;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存传输路由已经定位 Device 后交给其协议解码的一条原始上行消息。 */
public final class DeviceProtocolMessage {

    private final @NotNull Device device;

    private final @NotNull String code;

    private final byte @NotNull [] payload;

    private final @NotNull Instant observedAt;

    /**
     * @param device 传输路由已经定位并完整读取的 Device
     * @param code 协议内部稳定消息类型
     * @param payload 原始协议载荷
     * @param observedAt 平台收到时间
     */
    public DeviceProtocolMessage(
            @NotNull Device device,
            @NotNull String code,
            byte @NotNull [] payload,
            @NotNull Instant observedAt) {
        this.device = Objects.requireNonNull(device, "Device 协议消息的 Device 不能为空");
        this.code = TextUtils.requireText(code, "Device 协议消息 code");
        this.payload = Objects.requireNonNull(payload, "Device 协议消息载荷不能为空").clone();
        this.observedAt = Objects.requireNonNull(observedAt, "Device 协议消息观察时间不能为空");
    }

    /** @return 传输路由已经定位并完整读取的 Device */
    public @NotNull Device device() {
        return device;
    }

    /** @return 传输路由已经定位的 Device 标识 */
    public long deviceId() {
        return device.id();
    }

    /** @return 协议内部稳定消息类型 */
    public @NotNull String code() {
        return code;
    }

    /** @return 原始协议载荷副本 */
    public byte @NotNull [] payload() {
        return payload.clone();
    }

    /** @return 平台收到消息的时间 */
    public @NotNull Instant observedAt() {
        return observedAt;
    }
}
