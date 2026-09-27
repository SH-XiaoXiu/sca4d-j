package com.sca4d.app.server.iot.view;

import com.sca4d.auth.hmac.IssuedHmacKey;
import com.sca4d.auth.sharedsecret.IssuedSharedSecret;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回重置后的 Device Credential version 和只显示一次的 Secret 或 Key。 */
@SuppressWarnings("unused")
public final class IotSecretView {

    private final long version;

    private final @NotNull String secret;

    /** 创建只在当前响应中交付原始凭据的 HTTP 视图。 */
    private IotSecretView(long version, @NotNull String secret) {
        this.version = version;
        this.secret = secret;
    }

    /** @return Shared Secret 重置 HTTP 结果 */
    @CheckReturnValue
    public static @NotNull IotSecretView from(@NotNull IssuedSharedSecret result) {
        IssuedSharedSecret checked = Objects.requireNonNull(result, "Shared Secret 签发结果不能为空");
        return new IotSecretView(checked.credential().version().value(), checked.secret());
    }

    /** @return MAC Key 重置 HTTP 结果 */
    @CheckReturnValue
    public static @NotNull IotSecretView from(@NotNull IssuedHmacKey result) {
        IssuedHmacKey checked = Objects.requireNonNull(result, "HMAC Key 签发结果不能为空");
        return new IotSecretView(checked.credential().version().value(), checked.key());
    }

    /** @return 新 Credential version */
    public long getVersion() {
        return version;
    }

    /** @return 只在本次响应中显示的 Secret 或 Key */
    public @NotNull String getSecret() {
        return secret;
    }
}
