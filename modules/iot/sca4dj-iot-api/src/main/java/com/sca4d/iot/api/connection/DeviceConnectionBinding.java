package com.sca4d.iot.api.connection;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存一台 Device 可选的直接连接定义和唯一公开身份值。 */
public class DeviceConnectionBinding {

    private final @NotNull DeviceConnectionDefinition definition;

    private final @NotNull String identifierValue;

    /** @param definition 连接协议和身份类型 @param identifierValue Device 在该连接范围内的唯一公开身份 */
    public DeviceConnectionBinding(
            @NotNull DeviceConnectionDefinition definition,
            @NotNull String identifierValue) {
        this.definition = Objects.requireNonNull(definition, "Device 连接定义不能为空");
        this.identifierValue = TextUtils.requireText(identifierValue, "Device 连接身份值");
    }

    /** 创建指定连接协议、身份类型和值的绑定。 */
    public DeviceConnectionBinding(
            @NotNull DeviceConnectionProtocol protocol,
            @NotNull String identifierType,
            @NotNull String identifierValue) {
        this(new DeviceConnectionDefinition(protocol, identifierType), identifierValue);
    }

    /** @return 连接协议和身份类型 */
    public @NotNull DeviceConnectionDefinition definition() {
        return definition;
    }

    /** @return Device 在连接范围内的唯一公开身份 */
    public @NotNull String identifierValue() {
        return identifierValue;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof DeviceConnectionBinding binding
                        && definition.equals(binding.definition)
                        && identifierValue.equals(binding.identifierValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, identifierValue);
    }
}
