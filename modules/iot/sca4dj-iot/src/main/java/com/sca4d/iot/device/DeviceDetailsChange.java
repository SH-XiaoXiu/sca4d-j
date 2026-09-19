package com.sca4d.iot.device;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存不影响 Device 身份、凭据、协议和连接的展示资料修改。 */
public final class DeviceDetailsChange {

    private final @NotNull String displayName;

    private final @Nullable String remark;

    /** @param displayName 新展示名称 @param remark 新备注，null 表示清除 */
    public DeviceDetailsChange(@NotNull String displayName, @Nullable String remark) {
        this.displayName = Objects.requireNonNull(displayName, "Device 展示名称不能为空");
        this.remark = remark;
    }

    /** @return 新展示名称 */
    public @NotNull String displayName() {
        return displayName;
    }

    /** @return 新备注，null 表示清除 */
    public @Nullable String remark() {
        return remark;
    }
}
