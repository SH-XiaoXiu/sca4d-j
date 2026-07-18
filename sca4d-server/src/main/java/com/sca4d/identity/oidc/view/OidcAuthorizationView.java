package com.sca4d.identity.oidc.view;

import com.sca4d.identity.oidc.OidcAuthorizationStart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 浏览器跳转到 OpenID Provider 使用的授权地址和过期时间。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcAuthorizationView {

    private String authorizationUri;
    private Instant expiresAt;

    /** @return 包含公开 state、nonce 和 PKCE challenge，但不包含 PKCE verifier 的授权地址 */
    public static @NotNull OidcAuthorizationView from(@NotNull OidcAuthorizationStart authorization) {
        return new OidcAuthorizationView(
                authorization.getAuthorizationUri(), authorization.getExpiresAt());
    }
}
