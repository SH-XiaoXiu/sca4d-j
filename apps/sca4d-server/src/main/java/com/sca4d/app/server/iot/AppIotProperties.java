package com.sca4d.app.server.iot;

import com.sca4d.core.api.data.DataOps;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 保存 Sca4d Server 自有 IoT HTTP 行为的运行参数。 */
@ConfigurationProperties("app.iot")
public class AppIotProperties {

    private @Nullable String emqxWebhookSecret;

    private Duration deviceCreationCleanupInterval = Duration.ofMinutes(10);

    /** @return EMQX HTTP Authenticator 回调使用的 Secret */
    public @Nullable String getEmqxWebhookSecret() {
        return emqxWebhookSecret;
    }

    /** @param emqxWebhookSecret EMQX 回调通过请求头提交的独立 Secret */
    public void setEmqxWebhookSecret(@Nullable String emqxWebhookSecret) {
        this.emqxWebhookSecret = emqxWebhookSecret;
    }

    /** @return Device 创建幂等记录的清理间隔 */
    public @NotNull Duration getDeviceCreationCleanupInterval() {
        return deviceCreationCleanupInterval;
    }

    /** @param deviceCreationCleanupInterval Device 创建幂等记录的正数清理间隔 */
    public void setDeviceCreationCleanupInterval(@NotNull Duration deviceCreationCleanupInterval) {
        this.deviceCreationCleanupInterval = deviceCreationCleanupInterval;
    }

    /** 校验 App 自有 Device 创建幂等记录的清理参数。 */
    public void validateScheduling() {
        if (deviceCreationCleanupInterval == null
                || deviceCreationCleanupInterval.isZero()
                || deviceCreationCleanupInterval.isNegative()) {
            throw new IllegalStateException("app.iot.device-creation-cleanup-interval 必须为正数");
        }
    }

    /**
     * 读取启用 EMQX 时必须显式配置的非空回调 Secret。
     *
     * @return 去除首尾空白后的 EMQX 回调 Secret
     * @throws IllegalArgumentException 配置缺失或只有空白字符时抛出
     */
    @CheckReturnValue
    public @NotNull String emqxWebhookSecret() {
        return DataOps.s(emqxWebhookSecret)
                .strip()
                .req("app.iot.emqx-webhook-secret")
                .get();
    }

}
