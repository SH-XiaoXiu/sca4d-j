package com.sca4d.app.server.iot.view;

import com.sca4d.core.api.authentication.EstablishedSession;
import com.sca4d.core.api.authentication.SessionTokens;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 返回 Device 连接 MQTT 和定位服务端 Session 所需的一次性认证结果。 */
@SuppressWarnings("unused")
public final class IotDeviceSessionView {

    private final @NotNull String mqttToken;

    private final @NotNull String sessionId;

    private final @NotNull Instant expiresAt;

    /** 从通用 Session 建立结果提取 Device 需要的最小字段。 */
    private IotDeviceSessionView(@NotNull EstablishedSession establishment) {
        SessionTokens tokens = Objects.requireNonNull(establishment, "Session 建立结果不能为空").tokens();
        this.mqttToken = tokens.accessToken();
        this.sessionId = tokens.sessionId();
        this.expiresAt = tokens.sessionExpiresAt();
    }

    /** @return 不包含 Device Secret 的 HTTP Session 响应 */
    @CheckReturnValue
    public static @NotNull IotDeviceSessionView from(@NotNull EstablishedSession establishment) {
        return new IotDeviceSessionView(establishment);
    }

    /** @return MQTT CONNECT 使用且不能写入日志的 Session Token */
    public @NotNull String getMqttToken() {
        return mqttToken;
    }

    /** @return 服务端 backing Session 标识 */
    public @NotNull String getSessionId() {
        return sessionId;
    }

    /** @return MQTT Token 和 backing Session 的共同最晚有效时间 */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
}
