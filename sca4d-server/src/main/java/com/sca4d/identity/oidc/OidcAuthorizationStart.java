package com.sca4d.identity.oidc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** 浏览器跳转到 OpenID Provider 所需的授权地址和本次 state 过期时间。 */
@Getter
@AllArgsConstructor
public class OidcAuthorizationStart {

    private final String authorizationUri;
    private final Instant expiresAt;
}
