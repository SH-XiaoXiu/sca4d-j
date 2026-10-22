package com.sca4d.app.server.iot.view;

import com.sca4d.app.server.iot.IotDeviceCredentials;
import com.sca4d.app.server.iot.IotDeviceProfile;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 返回 Device 快照、可选 Account 和只在当前响应中显示的设备认证凭据。 */
@SuppressWarnings("unused")
public class IotDeviceCredentialView {

    private final @NotNull IotDeviceView device;

    private final @Nullable Long accountId;

    private final @Nullable String hmacKey;

    private final @Nullable String sharedSecret;

    /** 创建包含当前 Device 和一次性凭据的 HTTP 视图。 */
    private IotDeviceCredentialView(
            @NotNull IotDeviceView device,
            @Nullable Long accountId,
            @Nullable String sharedSecret,
            @Nullable String hmacKey) {
        this.device = device;
        this.accountId = accountId;
        this.sharedSecret = sharedSecret;
        this.hmacKey = hmacKey;
    }

    /** @return Device 创建或接入方式修改结果；没有新签发的凭据字段为空 */
    @CheckReturnValue
    public static @NotNull IotDeviceCredentialView from(
            @NotNull IotDeviceCredentials result,
            @NotNull IotDeviceProfile profile) {
        IotDeviceCredentials checked = Objects.requireNonNull(result, "Device 凭据结果不能为空");
        return new IotDeviceCredentialView(
                IotDeviceView.from(
                        checked.device(),
                        profile),
                checked.accountId().orElse(null),
                checked.sharedSecret().orElse(null),
                checked.hmacKey().orElse(null));
    }

    /** @return 修改后的 Device */
    public @NotNull IotDeviceView getDevice() {
        return device;
    }

    /** @return 认证 Device 的可选 Account 标识 */
    public @Nullable Long getAccountId() {
        return accountId;
    }

    /** @return 只允许管理端显示一次的可选 Shared Secret */
    public @Nullable String getSharedSecret() {
        return sharedSecret;
    }

    /** @return 只允许管理端显示一次的可选 MAC Key */
    public @Nullable String getHmacKey() {
        return hmacKey;
    }
}
