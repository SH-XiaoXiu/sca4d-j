package com.sca4d.auth.passkey.registration;

import com.sca4d.auth.passkey.PasskeyCeremonyId;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回浏览器开始 discoverable Passkey registration 所需的数据。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class PasskeyRegistrationOptions {

    private final PasskeyCeremonyId ceremonyId;

    private final String optionsJson;

    private final Instant expiresAt;

    /** 创建 registration ceremony 的公开结果。 */
    public PasskeyRegistrationOptions(
            @NotNull PasskeyCeremonyId ceremonyId,
            @NotNull String optionsJson,
            @NotNull Instant expiresAt) {
        this.ceremonyId = Objects.requireNonNull(ceremonyId, "Passkey ceremony 标识不能为空");
        this.optionsJson = Objects.requireNonNull(optionsJson, "Passkey registration options 不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Passkey ceremony 过期时间不能为空");
    }

    /** 返回完成 registration 时必须原样回传的标识。 */
    public @NotNull PasskeyCeremonyId ceremonyId() {
        return ceremonyId;
    }

    /** 返回可以直接传给浏览器原生 WebAuthn JSON API 的 options JSON。 */
    public @NotNull String optionsJson() {
        return optionsJson;
    }

    /** 返回 ceremony 失效时间。 */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
