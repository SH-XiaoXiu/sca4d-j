package com.sca4d.iot.api.connection;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 定义一种 Device 连接协议及其公开身份类型，不包含具体 Device 的身份值。 */
public final class DeviceConnectionDefinition {

    private final @NotNull DeviceConnectionProtocol protocol;

    private final @NotNull String identifierType;

    /** @param protocol MQTT、TCP 等连接协议 @param identifierType 该连接使用的公开身份类型 */
    public DeviceConnectionDefinition(
            @NotNull DeviceConnectionProtocol protocol,
            @NotNull String identifierType) {
        this.protocol = Objects.requireNonNull(protocol, "Device 连接协议不能为空");
        this.identifierType = TextUtils.requireText(identifierType, "Device 连接身份类型");
    }

    /** @return MQTT、TCP 等连接协议 */
    public @NotNull DeviceConnectionProtocol protocol() {
        return protocol;
    }

    /** @return 连接公开身份类型 */
    public @NotNull String identifierType() {
        return identifierType;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof DeviceConnectionDefinition definition
                        && protocol.equals(definition.protocol)
                        && identifierType.equals(definition.identifierType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, identifierType);
    }
}
