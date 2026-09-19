package com.sca4d.app.server.iot;

import org.jetbrains.annotations.Nullable;

/** 保存 Device 创建幂等记录解密后恢复结果所需的稳定字段。 */
public final class DeviceCreationReplay {

    private final long deviceId;

    private final @Nullable Long accountId;

    private final @Nullable String sharedSecret;

    private final @Nullable String hmacKey;

    /** 创建只在加密幂等响应中保存的 Device 创建结果。 */
    public DeviceCreationReplay(
            long deviceId,
            @Nullable Long accountId,
            @Nullable String sharedSecret,
            @Nullable String hmacKey) {
        this.deviceId = deviceId;
        this.accountId = accountId;
        this.sharedSecret = sharedSecret;
        this.hmacKey = hmacKey;
    }

    /** @return 新建 Device 标识 */
    public long deviceId() {
        return deviceId;
    }

    /** @return 认证 Device 的可选 Account 标识 */
    public @Nullable Long accountId() {
        return accountId;
    }

    /** @return 创建时签发的可选 Shared Secret 明文 */
    public @Nullable String sharedSecret() {
        return sharedSecret;
    }

    /** @return 创建时签发的可选 HMAC Key 明文 */
    public @Nullable String hmacKey() {
        return hmacKey;
    }
}
