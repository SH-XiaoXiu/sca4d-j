package com.sca4d.app.server.iot;

import com.sca4d.core.api.gateway.GatewayCallback;
import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayCallbackRequest;
import com.sca4d.core.api.gateway.GatewayCallbackResponse;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationRequest;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationResponse;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationService;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 验证 EMQX HTTP Authenticator 提交的共享 Secret，并执行 MQTT CONNECT 认证。 */
@Component
@ConditionalOnProperty(prefix = "app.iot.capabilities", name = "transport", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "sca4dj.iot.emqx", name = "enabled", havingValue = "true")
public final class EmqxAuthenticationCallback
        implements
            GatewayCallback<EmqxAuthenticationRequest, EmqxAuthenticationResponse> {

    private final @NotNull EmqxAuthenticationService authentication;

    private final @NotNull EmqxCallbackAuthenticator callbackAuthenticator;

    /**
     * 创建 EMQX 认证回调。
     *
     * @param authentication MQTT CONNECT 认证服务
     * @param callbackAuthenticator EMQX 回调来源认证器
     */
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // 认证服务由条件自动配置创建，IDE 无法静态关联。
    public EmqxAuthenticationCallback(
            @NotNull EmqxAuthenticationService authentication,
            @NotNull EmqxCallbackAuthenticator callbackAuthenticator) {
        this.authentication = Objects.requireNonNull(authentication, "EMQX 认证服务不能为空");
        this.callbackAuthenticator = Objects.requireNonNull(callbackAuthenticator, "EMQX 回调认证器不能为空");
    }

    /** @return `iot.emqx.authenticate` */
    @Override
    public @NotNull GatewayCallbackCode code() {
        return EmqxGatewayCallbacks.AUTHENTICATION;
    }

    /** @return EMQX HTTP Authenticator JSON 请求类型 */
    @Override
    public @NotNull Class<EmqxAuthenticationRequest> requestType() {
        return EmqxAuthenticationRequest.class;
    }

    /** @return 认证成功或拒绝时 EMQX 规定的 HTTP 200 JSON；共享 Secret 错误时返回 401 */
    @Override
    public @NotNull GatewayCallbackResponse<EmqxAuthenticationResponse> respond(
            @NotNull GatewayCallbackRequest<EmqxAuthenticationRequest> request) {
        callbackAuthenticator.authenticate(request.header(IotHttpHeaders.EMQX_SECRET).orElse(null));
        EmqxAuthenticationRequest body = Objects.requireNonNull(request.body(), "EMQX 认证回调请求体不能为空");
        return new GatewayCallbackResponse<>(200, authentication.authenticate(body));
    }
}
