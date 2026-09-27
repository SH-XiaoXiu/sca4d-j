package com.sca4d.iot.emqx.authentication;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.iot.api.connection.DeviceConnectionAccess;
import com.sca4d.iot.api.connection.DeviceConnectionAdmission;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 按 Device 明确接入方式验证 MQTT CONNECT，并生成 EMQX client_attrs、ACL 和失效时间。 */
public final class EmqxAuthenticationService {

    private static final Logger LOG = LoggerFactory.getLogger(EmqxAuthenticationService.class);

    private final @NotNull EmqxDeviceConnectionRequestFactory requests;

    private final @NotNull DeviceConnectionAdmission admission;

    private final @NotNull MqttAuthorizationService authorization;

    /** 创建不允许认证方式自动降级的 EMQX HTTP Authenticator 服务。 */
    public EmqxAuthenticationService(
            @NotNull EmqxDeviceConnectionRequestFactory requests,
            @NotNull DeviceConnectionAdmission admission,
            @NotNull MqttAuthorizationService authorization) {
        this.requests = Objects.requireNonNull(requests, "EMQX Device 连接请求工厂不能为空");
        this.admission = Objects.requireNonNull(admission, "Device 连接准入入口不能为空");
        this.authorization = Objects.requireNonNull(authorization, "MQTT 授权服务不能为空");
    }

    /**
     * 验证请求选择的唯一接入方式；所有预期认证失败都返回 `deny`，不返回 HTTP 错误或 `ignore`。
     *
     * @param request EMQX 从 CONNECT 和 TLS 通道提取的字段
     * @return EMQX `5.8.9` 认证响应
     */
    @CheckReturnValue
    public @NotNull EmqxAuthenticationResponse authenticate(@NotNull EmqxAuthenticationRequest request) {
        EmqxAuthenticationRequest input = Objects.requireNonNull(request, "EMQX 认证请求不能为空");
        try {
            DeviceConnectionAccess result = admission.admit(requests.create(input));
            MqttAuthorization resolved = authorization.resolve(result.device(), input.clientId());
            return EmqxAuthenticationResponse.allow(
                    clientAttributes(result, resolved),
                    resolved.rules(),
                    result.expiresAt().orElse(null));
        } catch (BusinessException | IllegalArgumentException exception) {
            LOG.warn("EMQX Device 认证已拒绝 | clientId={}", input.clientId());
            return EmqxAuthenticationResponse.deny();
        } catch (RuntimeException exception) {
            LOG.error(
                    "EMQX Device 认证依赖不可用，连接已拒绝 | clientId={}",
                    input.clientId(),
                    exception);
            return EmqxAuthenticationResponse.deny();
        }
    }

    /** 生成 Device、Product、协议、认证方式、ACL 指纹和可选 SessionId 连接属性。 */
    private static Map<String, String> clientAttributes(
            DeviceConnectionAccess result,
            MqttAuthorization authorization) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("device_id", Long.toString(result.device().id()));
        values.put("product_code", result.device().product().code());
        values.put("product_source", result.device().product().source().name());
        values.put("protocol_code", result.device().protocolCode());
        values.put("access_method", result.accessMethod().code().format(COLON));
        values.put(MqttAuthorizationService.FINGERPRINT_ATTRIBUTE, authorization.fingerprint());
        result.sessionId().ifPresent(value -> values.put("device_session_id", value.value()));
        return Map.copyOf(values);
    }

}
