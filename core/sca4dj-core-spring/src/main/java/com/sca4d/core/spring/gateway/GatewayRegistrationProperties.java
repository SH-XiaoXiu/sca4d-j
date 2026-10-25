package com.sca4d.core.spring.gateway;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** 保存应用实例向 Gateway 注册 HTTP 路由和回调所需的运行参数。 */
@ConfigurationProperties("sca4dj.gateway.registration")
public class GatewayRegistrationProperties {

    private boolean enabled;

    private @Nullable URI gatewayBaseUrl;

    private @Nullable URI serverBaseUrl;

    private @Nullable String instanceId;

    private @NotNull String readinessPath = "/actuator/health/readiness";

    private @Nullable String secret;

    private @NotNull Duration renewInterval = Duration.ofSeconds(10);

    private @NotNull Duration requestTimeout = Duration.ofSeconds(3);

    /** @return 是否在应用 readiness 后向 Gateway 注册 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled true 表示启动租约注册和续租 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return Gateway 控制 API 的基础地址 */
    public @Nullable URI getGatewayBaseUrl() {
        return gatewayBaseUrl;
    }

    /** @param gatewayBaseUrl Gateway 控制 API 的 HTTP 或 HTTPS 基础地址 */
    public void setGatewayBaseUrl(@Nullable URI gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    /** @return Gateway 可以访问的当前 Server 实例 HTTP(S) Origin */
    public @Nullable URI getServerBaseUrl() {
        return serverBaseUrl;
    }

    /** @param serverBaseUrl Gateway 转发和健康检查使用且不包含基础路径的 Server Origin */
    public void setServerBaseUrl(@Nullable URI serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl;
    }

    /**
     * @return 配置中的实例标识；注册时不读取该值，实际注册使用 `InstanceProperties` 的实例标识
     */
    public @Nullable String getInstanceId() {
        return instanceId;
    }

    /**
     * @param instanceId 配置的实例标识；修改该值不会改变 `GatewayRegistrationLifecycle` 注册时使用的实例标识
     */
    public void setInstanceId(@Nullable String instanceId) {
        this.instanceId = instanceId;
    }

    /** @return Gateway 主动检查的 readiness 路径 */
    public @NotNull String getReadinessPath() {
        return readinessPath;
    }

    /** @param readinessPath 相对 Server Origin、以斜杠开头的 readiness 路径 */
    public void setReadinessPath(@NotNull String readinessPath) {
        this.readinessPath = readinessPath;
    }

    /** @return 注册控制请求和可信转发共同使用的 Secret */
    public @Nullable String getSecret() {
        return secret;
    }

    /** @param secret 不得记录或发给业务调用方的 Gateway 控制 Secret */
    public void setSecret(@Nullable String secret) {
        this.secret = secret;
    }

    /** @return 两次注册或续租之间的间隔；注册或续租失败后也按同一间隔重试 */
    public @NotNull Duration getRenewInterval() {
        return renewInterval;
    }

    /** @param renewInterval 小于 Gateway 租约 TTL 的正数间隔 */
    public void setRenewInterval(@NotNull Duration renewInterval) {
        this.renewInterval = renewInterval;
    }

    /** @return 单次注册、续租或注销请求的超时 */
    public @NotNull Duration getRequestTimeout() {
        return requestTimeout;
    }

    /** @param requestTimeout 正数 HTTP 请求超时 */
    public void setRequestTimeout(@NotNull Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
