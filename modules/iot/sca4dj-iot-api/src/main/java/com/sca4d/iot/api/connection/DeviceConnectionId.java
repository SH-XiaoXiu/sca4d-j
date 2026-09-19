package com.sca4d.iot.api.connection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 标识一个设备接入实现观测到的明确连接代次。 */
public final class DeviceConnectionId {

    private final @NotNull String value;

    /**
     * @param value 部署范围内唯一且不包含 Device、节点或协议字段的不透明标识
     */
    @JsonCreator
    public DeviceConnectionId(@JsonProperty("value") @NotNull String value) {
        String checked = TextUtils.requireText(value, "设备连接标识");
        if (checked.length() > 128) {
            throw new IllegalArgumentException("设备连接标识不能超过 128 个字符");
        }
        this.value = checked;
    }

    /** @return 不透明设备连接标识 */
    @Contract(pure = true)
    @JsonProperty("value")
    public @NotNull String value() {
        return value;
    }

    /** @return 两个标识是否表示同一设备连接代次 */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof DeviceConnectionId that && value.equals(that.value);
    }

    /** @return 完整标识的哈希值 */
    @Override
    @Contract(pure = true)
    public int hashCode() {
        return value.hashCode();
    }

    /** @return 不透明设备连接标识 */
    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
