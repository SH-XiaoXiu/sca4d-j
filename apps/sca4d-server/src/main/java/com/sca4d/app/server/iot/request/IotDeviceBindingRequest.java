package com.sca4d.app.server.iot.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收停用 Device 的目标物模型版本和设备协议 code。 */
@SuppressWarnings("unused")
public class IotDeviceBindingRequest {

    private int thingModelVersion;

    @NotBlank
    @Size(max = 128)
    private @Nullable String protocolCode;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotDeviceBindingRequest() {
    }

    /** @return 目标物模型版本 */
    public int getThingModelVersion() {
        return thingModelVersion;
    }

    /** @param thingModelVersion 目标物模型版本 */
    public void setThingModelVersion(int thingModelVersion) {
        this.thingModelVersion = thingModelVersion;
    }

    /** @return 目标协议 code */
    public @NotNull String getProtocolCode() {
        return Objects.requireNonNull(protocolCode);
    }

    /** @param protocolCode 目标协议 code */
    public void setProtocolCode(@Nullable String protocolCode) {
        this.protocolCode = protocolCode;
    }

}
