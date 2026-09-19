package com.sca4d.app.server.iot.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/** 接收停用 Device 的可选直接连接绑定、认证接入方式和可选 X.509 证书。 */
@SuppressWarnings("unused")
public final class IotDeviceConnectionRequest {

    @Size(max = 128)
    private @Nullable String connectionProtocol;

    @Size(max = 128)
    private @Nullable String connectionIdentityType;

    @Size(max = 512)
    private @Nullable String connectionIdentity;

    @Req.NotNull
    @Size(max = 16)
    private @Nullable Set<@NotBlank @Size(max = 128) String> accessMethods;

    @Size(max = 65536)
    private @Nullable String certificateDerBase64;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotDeviceConnectionRequest() {
    }

    /** @return 可选直接连接协议 code */
    public @Nullable String getConnectionProtocol() {
        return connectionProtocol;
    }

    /** @param connectionProtocol 可选直接连接协议 code */
    public void setConnectionProtocol(@Nullable String connectionProtocol) {
        this.connectionProtocol = connectionProtocol;
    }

    /** @return 可选直接连接身份类型 */
    public @Nullable String getConnectionIdentityType() {
        return connectionIdentityType;
    }

    /** @param connectionIdentityType 可选直接连接身份类型 */
    public void setConnectionIdentityType(@Nullable String connectionIdentityType) {
        this.connectionIdentityType = connectionIdentityType;
    }

    /** @return 可选直接连接身份值 */
    public @Nullable String getConnectionIdentity() {
        return connectionIdentity;
    }

    /** @param connectionIdentity 可选直接连接身份值 */
    public void setConnectionIdentity(@Nullable String connectionIdentity) {
        this.connectionIdentity = connectionIdentity;
    }

    /** @return 完整目标认证接入方式；间接 Device 为空 */
    public @NotNull Set<String> getAccessMethods() {
        return Set.copyOf(Objects.requireNonNull(accessMethods));
    }

    /** @param accessMethods 完整目标认证接入方式；间接 Device 传空集合 */
    public void setAccessMethods(@Nullable Set<String> accessMethods) {
        this.accessMethods = accessMethods == null ? null : Set.copyOf(accessMethods);
    }

    /** @return 可选新增证书 DER Base64 */
    public @Nullable String getCertificateDerBase64() {
        return certificateDerBase64;
    }

    /** @param certificateDerBase64 可选新增证书 DER Base64 */
    public void setCertificateDerBase64(@Nullable String certificateDerBase64) {
        this.certificateDerBase64 = certificateDerBase64;
    }
}
