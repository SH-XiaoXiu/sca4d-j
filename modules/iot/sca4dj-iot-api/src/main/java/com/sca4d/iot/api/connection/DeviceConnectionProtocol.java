package com.sca4d.iot.api.connection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 使用开放 code 标识设备建立连接时采用的应用协议。 */
public final class DeviceConnectionProtocol {

    /** MQTT；MQTT over TCP 和 MQTT over WebSocket 使用同一个协议值。 */
    public static final @NotNull DeviceConnectionProtocol MQTT = new DeviceConnectionProtocol("mqtt");

    /** 厂商直接使用 TCP 字节流定义的协议。 */
    public static final @NotNull DeviceConnectionProtocol TCP = new DeviceConnectionProtocol("tcp");

    /** 厂商直接使用 WebSocket 消息定义的协议。 */
    public static final @NotNull DeviceConnectionProtocol WEBSOCKET = new DeviceConnectionProtocol("websocket");

    private final @NotNull String code;

    /**
     * @param code 具体设备接入实现声明的稳定非空白协议 code
     */
    @JsonCreator
    public DeviceConnectionProtocol(@JsonProperty("code") @NotNull String code) {
        String checked = TextUtils.requireText(code, "设备连接协议");
        if (checked.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("设备连接协议不能包含空白");
        }
        this.code = checked;
    }

    /** @return 设备连接协议的稳定 code */
    @Contract(pure = true)
    @JsonProperty("code")
    public @NotNull String code() {
        return code;
    }

    /** @return 两个协议是否使用相同 code */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof DeviceConnectionProtocol protocol && code.equals(protocol.code);
    }

    /** @return 协议 code 的哈希值 */
    @Override
    @Contract(pure = true)
    public int hashCode() {
        return code.hashCode();
    }

    /** @return 设备连接协议的稳定 code */
    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return code;
    }
}
