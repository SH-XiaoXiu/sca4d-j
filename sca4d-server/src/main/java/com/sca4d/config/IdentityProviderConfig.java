package com.sca4d.config;

import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** 注册外部 OIDC 和 OAuth2 请求共用的 HTTP 客户端及超时配置。 */
@Configuration
@EnableConfigurationProperties(Sca4djIdentityProviderProperties.class)
public class IdentityProviderConfig {

    /**
     * 创建不自动跟随重定向的外部身份源 HTTP 客户端。
     *
     * @return 使用部署配置连接和读取超时的同步客户端
     */
    @Bean
    public @NotNull RestClient identityProviderRestClient(
            @NotNull Sca4djIdentityProviderProperties properties) {
        Duration connectionTimeout = requireTimeout(
                properties.getConnectionTimeout(), "connection-timeout");
        Duration readTimeout = requireTimeout(properties.getReadTimeout(), "read-timeout");
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectionTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /** @return 外部连接允许使用的正数超时 */
    private @NotNull Duration requireTimeout(@Nullable Duration value, @NotNull String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("sca4dj.identity-provider." + name + " 必须是正数 Duration");
        }
        return value;
    }
}
