package com.sca4d.iot.api.connection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 请求一种设备接入实现终止一个明确连接代次。 */
public final class DeviceDisconnectMessage extends Message {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DEVICE/DISCONNECT");

    public static final int PROTOCOL_VERSION = 1;

    private final long deviceId;

    private final @NotNull DeviceConnectionId connectionId;

    private final @NotNull DeviceConnectionProtocol protocol;

    private final @NotNull String reason;

    private final int attempt;

    /** 创建可由通用 Messaging 可靠传递的断连请求。 */
    @JsonCreator
    public DeviceDisconnectMessage(
            @JsonProperty("deviceId") long deviceId,
            @JsonProperty("connectionId") @NotNull DeviceConnectionId connectionId,
            @JsonProperty("protocol") @NotNull DeviceConnectionProtocol protocol,
            @JsonProperty("reason") @NotNull String reason,
            @JsonProperty("attempt") int attempt) {
        if (deviceId <= 0 || attempt <= 0) {
            throw new IllegalArgumentException("Device 标识和断连尝试序号必须为正数");
        }
        this.deviceId = deviceId;
        this.connectionId = Objects.requireNonNull(connectionId, "DeviceConnectionId 不能为空");
        this.protocol = Objects.requireNonNull(protocol, "DeviceConnectionProtocol 不能为空");
        this.reason = TextUtils.requireText(reason, "断连原因");
        this.attempt = attempt;
    }

    /** @return 连接所属 Device 标识 */
    @JsonProperty("deviceId")
    public long deviceId() {
        return deviceId;
    }

    /** @return 接入实现登记的明确连接代次 */
    @JsonProperty("connectionId")
    public @NotNull DeviceConnectionId connectionId() {
        return connectionId;
    }

    /** @return 负责终止连接的协议编码 */
    @JsonProperty("protocol")
    public @NotNull DeviceConnectionProtocol protocol() {
        return protocol;
    }

    /** @return 不包含凭据和隐私数据的稳定终止原因 */
    @JsonProperty("reason")
    public @NotNull String reason() {
        return reason;
    }

    /** @return 从 1 开始的断连尝试序号 */
    @JsonProperty("attempt")
    public int attempt() {
        return attempt;
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }
}
