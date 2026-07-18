package com.sca4d.identity.oauth2.view;

import com.sca4d.identity.oauth2.OAuth2AuthorizationStart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/** 浏览器跳转到 OAuth2 Provider 使用的授权地址和过期时间。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2AuthorizationView {

    private String authorizationUri;
    private Instant expiresAt;

    public static @NotNull OAuth2AuthorizationView from(
            @NotNull OAuth2AuthorizationStart authorization) {
        return new OAuth2AuthorizationView(
                authorization.getAuthorizationUri(), authorization.getExpiresAt());
    }
}
