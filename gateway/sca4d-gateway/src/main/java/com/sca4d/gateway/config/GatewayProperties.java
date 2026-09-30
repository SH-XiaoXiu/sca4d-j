package com.sca4d.gateway.config;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.regex.Pattern;

/** 保存 Gateway 注册控制、Server readiness 检查和注册后端标识。 */
@ConfigurationProperties("sca4dj.gateway")
public class GatewayProperties {
    private int healthConcurrency = 8;

    private static final Pattern SERVICE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private @Nullable String secret;

    private @NotNull String backendServiceId = "sca4d-server";

    private @NotNull Duration leaseTtl = Duration.ofSeconds(30);

    private @NotNull Duration healthInterval = Duration.ofSeconds(5);

    private @NotNull Duration healthTimeout = Duration.ofSeconds(3);

    /** @return 控制 API 和转发到 Server 时使用的 Secret */
    public @Nullable String getSecret() {
        return secret;
    }

    /** @param secret 不得转发给外部调用方或写入日志的 Gateway Secret */
    public void setSecret(@Nullable String secret) {
        this.secret = secret;
    }

    /** @return 同一轮 readiness 检查的最大并发请求数 */
    public int getHealthConcurrency() {
        return healthConcurrency;
    }

    /** @param healthConcurrency 正数并发上限 */
    public void setHealthConcurrency(int healthConcurrency) {
        this.healthConcurrency = healthConcurrency;
    }

    /** @return Spring Cloud LoadBalancer 中表示当前注册 Server 集群的 service ID */
    public @NotNull String getBackendServiceId() {
        return backendServiceId;
    }

    /** @param backendServiceId Gateway 内部注册后端使用的稳定 service ID */
    public void setBackendServiceId(@NotNull String backendServiceId) {
        this.backendServiceId = backendServiceId;
    }

    /** @return Server 未续租后保留注册的时长 */
    public @NotNull Duration getLeaseTtl() {
        return leaseTtl;
    }

    /** @param leaseTtl 大于 Server 续租间隔的正数时长 */
    public void setLeaseTtl(@NotNull Duration leaseTtl) {
        this.leaseTtl = leaseTtl;
    }

    /** @return Gateway 主动检查 Server readiness 的间隔 */
    public @NotNull Duration getHealthInterval() {
        return healthInterval;
    }

    /** @param healthInterval 正数检查间隔 */
    public void setHealthInterval(@NotNull Duration healthInterval) {
        this.healthInterval = healthInterval;
    }

    /** @return 单次 readiness 检查的请求超时 */
    public @NotNull Duration getHealthTimeout() {
        return healthTimeout;
    }

    /** @param healthTimeout 正数 HTTP 请求超时 */
    public void setHealthTimeout(@NotNull Duration healthTimeout) {
        this.healthTimeout = healthTimeout;
    }

    /** @return 去除首尾空白后的必填 Gateway Secret */
    @CheckReturnValue
    public @NotNull String validatedSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("sca4dj.gateway.secret 不能为空");
        }
        return secret.strip();
    }

    /** @return 去除首尾空白并通过格式校验的注册后端 service ID */
    @CheckReturnValue
    public @NotNull String validatedBackendServiceId() {
        String value = backendServiceId.strip();
        if (!SERVICE_ID.matcher(value).matches()) {
            throw new IllegalStateException(
                    "sca4dj.gateway.backend-service-id 必须匹配 [A-Za-z0-9][A-Za-z0-9._-]{0,127}");
        }
        return value;
    }

    /**
     * 校验注册后端标识、租约和健康检查配置。
     *
     * @throws IllegalStateException 任一必填值缺失或格式不合法时抛出
     */
    public void validate() {
        positive(leaseTtl, "sca4dj.gateway.lease-ttl");
        positive(healthInterval, "sca4dj.gateway.health-interval");
        positive(healthTimeout, "sca4dj.gateway.health-timeout");
        if (healthConcurrency < 1) {
            throw new IllegalStateException("sca4dj.gateway.health-concurrency 必须为正数");
        }
        backendServiceId = validatedBackendServiceId();
    }

    /** 校验配置 Duration 为正数。 */
    private static void positive(@Nullable Duration value, @NotNull String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " 必须为正数");
        }
    }

}
