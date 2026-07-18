package com.sca4d.authentication.passkey;

import org.jetbrains.annotations.NotNull;

/** 新建 Passkey assertion 及浏览器调用 {@code navigator.credentials.get()} 的 JSON。 */
public final class PasskeyAssertionStart {
    private final PasskeyAssertion assertion;
    private final String browserOptionsJson;

    public PasskeyAssertionStart(
            @NotNull PasskeyAssertion assertion, @NotNull String browserOptionsJson) {
        this.assertion = assertion;
        this.browserOptionsJson = browserOptionsJson;
    }

    public @NotNull PasskeyAssertion getAssertion() {
        return assertion;
    }

    public @NotNull String getBrowserOptionsJson() {
        return browserOptionsJson;
    }
}
