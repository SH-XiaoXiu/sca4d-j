package com.sca4d.iam.passkey;

import org.jetbrains.annotations.NotNull;

/** 新建 Passkey registration 及浏览器调用 {@code navigator.credentials.create()} 的 JSON。 */
public final class PasskeyRegistrationStart {
    private final PasskeyRegistration registration;
    private final String browserOptionsJson;

    public PasskeyRegistrationStart(
            @NotNull PasskeyRegistration registration, @NotNull String browserOptionsJson) {
        this.registration = registration;
        this.browserOptionsJson = browserOptionsJson;
    }

    public @NotNull PasskeyRegistration getRegistration() {
        return registration;
    }

    public @NotNull String getBrowserOptionsJson() {
        return browserOptionsJson;
    }
}
