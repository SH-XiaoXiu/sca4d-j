package com.sca4d.app.server.authentication.view;

import org.jetbrains.annotations.NotNull;

/** 返回上游授权地址，不返回授权状态中的 PKCE 或 Nonce。 */
public final class FederatedAuthorizationView {
    private final String authorizationUrl;

    private FederatedAuthorizationView(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    /** @return 上游授权地址 */
    public static @NotNull FederatedAuthorizationView from(@NotNull String authorizationUrl) {
        return new FederatedAuthorizationView(authorizationUrl);
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }
}
