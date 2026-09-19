package com.sca4d.app.server.iot.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收 Device 的展示名称和备注。 */
@SuppressWarnings("unused")
public final class IotDeviceDetailsRequest {

    @NotBlank
    @Size(max = 128)
    private @Nullable String displayName;

    @Size(max = 4096)
    private @Nullable String remark;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotDeviceDetailsRequest() {
    }

    /** @return 新展示名称 */
    public @NotNull String getDisplayName() {
        return Objects.requireNonNull(displayName);
    }

    /** @param displayName 新展示名称 */
    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    /** @return 新的可选备注 */
    public @Nullable String getRemark() {
        return remark;
    }

    /** @param remark 新的可选备注 */
    public void setRemark(@Nullable String remark) {
        this.remark = remark;
    }
}
