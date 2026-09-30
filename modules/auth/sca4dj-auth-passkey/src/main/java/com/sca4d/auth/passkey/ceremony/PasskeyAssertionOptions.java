package com.sca4d.auth.passkey.ceremony;

import com.sca4d.auth.passkey.PasskeyCeremonyId;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回浏览器开始 discoverable Passkey assertion 所需的数据。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PasskeyAssertionOptions {

    private final Instant expiresAt;

    private final PasskeyCeremonyId ceremonyId;

    private final String optionsJson;

    public PasskeyAssertionOptions(
            @NotNull PasskeyCeremonyId ceremonyId,
            @NotNull String optionsJson,
            @NotNull Instant expiresAt) {
        this.ceremonyId = Objects.requireNonNull(ceremonyId, "Passkey ceremony 标识不能为空");
        this.optionsJson = Objects.requireNonNull(optionsJson, "Passkey assertion options 不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Passkey ceremony 过期时间不能为空");
    }

    /**
     * 完成 assertion 时必须原样回传的标识。
     *
     * @return 完成 assertion 时必须原样回传的标识
     */
    public @NotNull PasskeyCeremonyId ceremonyId() {
        return ceremonyId;
    }

    /**
     * 可以直接传给浏览器原生 WebAuthn JSON API 的 options JSON。
     *
     * @return 可以直接传给浏览器原生 WebAuthn JSON API 的 options JSON
     */
    public @NotNull String optionsJson() {
        return optionsJson;
    }

    /**
     * ceremony 失效时间。
     *
     * @return ceremony 失效时间
     */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }
}
