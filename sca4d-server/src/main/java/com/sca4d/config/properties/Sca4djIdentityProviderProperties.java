package com.sca4d.config.properties;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** 外部身份源 Discovery、Token、Profile 和 LDAP 请求使用的连接与读取超时。 */
@ConfigurationProperties(prefix = "sca4dj.identity-provider")
public class Sca4djIdentityProviderProperties {

    private Duration connectionTimeout;
    private Duration readTimeout;
    private Oidc oidc = new Oidc();
    private OAuth2 oauth2 = new OAuth2();

    public @Nullable Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(@Nullable Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public @Nullable Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(@Nullable Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public void setOidc(Oidc oidc) {
        this.oidc = oidc;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public void setOauth2(OAuth2 oauth2) {
        this.oauth2 = oauth2;
    }

    /** OIDC Authorization Code 请求的 Redis、回调和浏览器完成地址。 */
    public static class Oidc {

        private String authorizationKeyPrefix = "sca4dj:identity:oidc-authorization";
        private Duration authorizationTtl = Duration.ofMinutes(5);
        private URI redirectUri;
        private URI completionUri;

        public String getAuthorizationKeyPrefix() {
            return authorizationKeyPrefix;
        }

        public void setAuthorizationKeyPrefix(String authorizationKeyPrefix) {
            this.authorizationKeyPrefix = authorizationKeyPrefix;
        }

        public Duration getAuthorizationTtl() {
            return authorizationTtl;
        }

        public void setAuthorizationTtl(Duration authorizationTtl) {
            this.authorizationTtl = authorizationTtl;
        }

        public @Nullable URI getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(@Nullable URI redirectUri) {
            this.redirectUri = redirectUri;
        }

        public @Nullable URI getCompletionUri() {
            return completionUri;
        }

        public void setCompletionUri(@Nullable URI completionUri) {
            this.completionUri = completionUri;
        }
    }

    /** OAuth2 Authorization Code 请求的 Redis、回调和浏览器完成地址。 */
    public static class OAuth2 {

        private String authorizationKeyPrefix = "sca4dj:identity:oauth2-authorization";
        private Duration authorizationTtl = Duration.ofMinutes(5);
        private URI redirectUri;
        private URI completionUri;

        public String getAuthorizationKeyPrefix() {
            return authorizationKeyPrefix;
        }

        public void setAuthorizationKeyPrefix(String authorizationKeyPrefix) {
            this.authorizationKeyPrefix = authorizationKeyPrefix;
        }

        public Duration getAuthorizationTtl() {
            return authorizationTtl;
        }

        public void setAuthorizationTtl(Duration authorizationTtl) {
            this.authorizationTtl = authorizationTtl;
        }

        public @Nullable URI getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(@Nullable URI redirectUri) {
            this.redirectUri = redirectUri;
        }

        public @Nullable URI getCompletionUri() {
            return completionUri;
        }

        public void setCompletionUri(@Nullable URI completionUri) {
            this.completionUri = completionUri;
        }
    }
}
