package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.passkey.ceremony.PasskeyAssertionOptions;
import com.sca4d.auth.passkey.registration.PasskeyRegistrationOptions;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回浏览器原生 WebAuthn JSON API 所需的 options 和 ceremony 标识。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public final class PasskeyOptionsView {

    private final String ceremonyId;

    private final String optionsJson;

    private final Instant expiresAt;

    private PasskeyOptionsView(String ceremonyId, String optionsJson, Instant expiresAt) {
        this.ceremonyId = ceremonyId;
        this.optionsJson = optionsJson;
        this.expiresAt = expiresAt;
    }

    /** 创建 assertion options 的 HTTP 投影。 */
    public static @NotNull PasskeyOptionsView from(@NotNull PasskeyAssertionOptions options) {
        PasskeyAssertionOptions checked = Objects.requireNonNull(options, "Passkey assertion options 不能为空");
        return new PasskeyOptionsView(
                checked.ceremonyId().value(),
                checked.optionsJson(),
                checked.expiresAt());
    }

    /** 创建 registration options 的 HTTP 投影。 */
    public static @NotNull PasskeyOptionsView from(@NotNull PasskeyRegistrationOptions options) {
        PasskeyRegistrationOptions checked = Objects.requireNonNull(options, "Passkey registration options 不能为空");
        return new PasskeyOptionsView(
                checked.ceremonyId().value(),
                checked.optionsJson(),
                checked.expiresAt());
    }

    public String getCeremonyId() {
        return ceremonyId;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
