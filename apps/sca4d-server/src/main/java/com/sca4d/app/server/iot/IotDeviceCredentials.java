package com.sca4d.app.server.iot;

import com.sca4d.iot.api.Device;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** 保存 Sca4d Server 创建或修改 Device 时产生的 Account 和一次性认证凭据。 */
public class IotDeviceCredentials {

    private final @NotNull Device device;

    private final @Nullable Long accountId;

    private final @Nullable String hmacKey;

    private final @Nullable String sharedSecret;

    /** 创建只允许当前应用调用结果读取的一次性凭据。 */
    public IotDeviceCredentials(
            @NotNull Device device,
            @Nullable Long accountId,
            @Nullable String sharedSecret,
            @Nullable String hmacKey) {
        this.device = Objects.requireNonNull(device, "Device 不能为空");
        this.accountId = accountId;
        this.sharedSecret = sharedSecret;
        this.hmacKey = hmacKey;
    }

    /** @return 创建或修改后的 Device */
    public @NotNull Device device() {
        return device;
    }

    /** @return 认证 Device 的 Account；只识别 Device 没有 Account 时为空 */
    public @NotNull Optional<Long> accountId() {
        return Optional.ofNullable(accountId);
    }

    /** @return 本次新签发的 Shared Secret；没有签发时为空 */
    public @NotNull Optional<String> sharedSecret() {
        return Optional.ofNullable(sharedSecret);
    }

    /** @return 本次新签发的 MAC Key；没有签发时为空 */
    public @NotNull Optional<String> hmacKey() {
        return Optional.ofNullable(hmacKey);
    }
}
