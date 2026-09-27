package com.sca4d.app.server.iot;

import cn.hutool.core.util.StrUtil;
import com.sca4d.auth.sharedsecret.SharedSecretAuthenticationMethod;
import com.sca4d.auth.sharedsecret.SharedSecretProof;
import com.sca4d.auth.x509.X509AuthenticationMethod;
import com.sca4d.auth.x509.X509TransportProof;
import com.sca4d.core.api.authentication.AuthenticationSubmission;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.connection.DeviceConnectionRequest;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationRequest;
import com.sca4d.iot.emqx.authentication.EmqxDeviceConnectionRequestFactory;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

/** 把 EMQX CONNECT 字段映射为 Sca4d Server 安装的匿名、Session、Shared Secret 或 X.509 输入。 */
@Component
public final class Sca4dEmqxDeviceConnectionRequestFactory implements EmqxDeviceConnectionRequestFactory {

    /**
     * 把示例 App 约定的 MQTT username 解析为接入方式，并且只创建一种连接输入。
     *
     * @param request EMQX 从 CONNECT 和 TLS 通道提取的字段
     * @return 使用 MQTT Client ID 作为公开 Device 身份的连接请求
     */
    @Override
    @CheckReturnValue
    public @NotNull DeviceConnectionRequest create(@NotNull EmqxAuthenticationRequest request) {
        EmqxAuthenticationRequest input = Objects.requireNonNull(request, "EMQX 认证请求不能为空");
        DeviceAccessMethod accessMethod = DeviceAccessMethod.parse(
                TextUtils.requireText(input.username(), "MQTT username 接入方式"));
        if (accessMethod.anonymousAccess()) {
            if (StrUtil.isNotBlank(input.password()) || StrUtil.isNotBlank(input.certificatePem())) {
                throw new IllegalArgumentException("anonymous 接入不接受认证材料");
            }
            return DeviceConnectionRequest.anonymous(MqttDeviceConnection.clientId(input.clientId()));
        }
        if (accessMethod.usesSession()) {
            if (StrUtil.isNotBlank(input.certificatePem())) {
                throw new IllegalArgumentException("session 接入不接受客户端证书");
            }
            return DeviceConnectionRequest.session(
                    MqttDeviceConnection.clientId(input.clientId()),
                    TextUtils.requireText(input.password(), "Session Token"));
        }
        TreeCode methodCode = accessMethod.authenticationMethodCode()
                .orElseThrow(() -> new IllegalArgumentException("Account 接入缺少认证方法编码"));
        if (SharedSecretAuthenticationMethod.CODE.equals(methodCode)) {
            if (StrUtil.isNotBlank(input.certificatePem())) {
                throw new IllegalArgumentException("Shared Secret 接入不接受客户端证书");
            }
            return DeviceConnectionRequest.authenticated(
                    MqttDeviceConnection.clientId(input.clientId()),
                    new AuthenticationSubmission<>(
                            SharedSecretAuthenticationMethod.CODE,
                            SharedSecretProof.class,
                            new SharedSecretProof(TextUtils.requireText(input.password(), "Shared Secret"))));
        }
        if (X509AuthenticationMethod.CODE.equals(methodCode)) {
            if (StrUtil.isNotBlank(input.password())) {
                throw new IllegalArgumentException("X.509 接入不接受 password");
            }
            return DeviceConnectionRequest.authenticated(
                    MqttDeviceConnection.clientId(input.clientId()),
                    new AuthenticationSubmission<>(
                            X509AuthenticationMethod.CODE,
                            X509TransportProof.class,
                            X509TransportProof.verified(
                                    certificateDer(
                                            TextUtils.requireText(input.certificatePem(), "客户端证书 PEM")))));
        }
        throw new IllegalArgumentException("Sca4d Server 没有为 EMQX 安装该 Device 接入方式");
    }

    /** 从 EMQX 已验证的公开 PEM 证书提取 X.509 Proof 需要的 DER。 */
    private static byte @NotNull [] certificateDer(@NotNull String pem) {
        try {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            return certificate.getEncoded();
        } catch (CertificateException exception) {
            throw new IllegalArgumentException("客户端证书无法解析", exception);
        }
    }
}
