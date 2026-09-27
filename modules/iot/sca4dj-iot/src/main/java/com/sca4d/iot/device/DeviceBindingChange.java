package com.sca4d.iot.device;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存停用 Device 后一次物模型版本和协议实现的显式切换。 */
public class DeviceBindingChange {

    private final int thingModelVersion;

    private final @NotNull String protocolCode;

    /**
     * @param thingModelVersion 目标已发布版本
     * @param protocolCode 目标兼容协议 code
     */
    public DeviceBindingChange(
            int thingModelVersion,
            @NotNull String protocolCode) {
        this.thingModelVersion = thingModelVersion;
        this.protocolCode = Objects.requireNonNull(protocolCode, "Device 协议 code 不能为空");
        if (thingModelVersion <= 0)
            throw new IllegalArgumentException("物模型版本必须为正数");
    }

    /** @return 目标物模型版本 */
    public int thingModelVersion() {
        return thingModelVersion;
    }

    /** @return 目标协议 code */
    public @NotNull String protocolCode() {
        return protocolCode;
    }

}
