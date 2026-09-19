package com.sca4d.app.server.iot.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/** 接收创建 Device、绑定物模型和可选 X.509 公钥证书所需的完整字段。 */
@SuppressWarnings("unused")
public final class IotDeviceRequest {

    private long productId;

    @NotBlank
    @Size(max = 128)
    private @Nullable String code;

    @Size(max = 512)
    private @Nullable String serialNumber;

    @NotBlank
    @Size(max = 128)
    private @Nullable String displayName;

    @Size(max = 4096)
    private @Nullable String remark;

    private int thingModelVersion;

    @NotBlank
    @Size(max = 128)
    private @Nullable String protocolCode;

    @Size(max = 128)
    private @Nullable String connectionProtocol;

    @Size(max = 128)
    private @Nullable String connectionIdentityType;

    @Size(max = 256)
    private @Nullable String connectionIdentity;

    @Req.NotNull
    @Size(max = 16)
    private @Nullable Set<@NotBlank @Size(max = 128) String> accessMethods;

    @Size(max = 65536)
    private @Nullable String certificateDerBase64;

    /** 创建供 Jackson 绑定的空请求。 */
    public IotDeviceRequest() {
    }

    /** @return Device 所属 Product 标识 */
    public long getProductId() {
        return productId;
    }

    /** @param productId Device 所属 Product 标识 */
    public void setProductId(long productId) {
        this.productId = productId;
    }

    /** @return Product 内唯一 Device code */
    public @NotNull String getCode() {
        return Objects.requireNonNull(code);
    }

    /** @param code Product 内唯一 Device code */
    public void setCode(@Nullable String code) {
        this.code = code;
    }

    /** @return 可选 Product 内唯一序列号 */
    public @Nullable String getSerialNumber() {
        return serialNumber;
    }

    /** @param serialNumber 可选 Product 内唯一序列号 */
    public void setSerialNumber(@Nullable String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /** @return Device 展示名称 */
    public @NotNull String getDisplayName() {
        return Objects.requireNonNull(displayName);
    }

    /** @param displayName Device 展示名称 */
    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    /** @return 可选管理备注 */
    public @Nullable String getRemark() {
        return remark;
    }

    /** @param remark 可选管理备注 */
    public void setRemark(@Nullable String remark) {
        this.remark = remark;
    }

    /** @return 显式绑定的物模型版本 */
    public int getThingModelVersion() {
        return thingModelVersion;
    }

    /** @param thingModelVersion 显式绑定的物模型版本 */
    public void setThingModelVersion(int thingModelVersion) {
        this.thingModelVersion = thingModelVersion;
    }

    /** @return Product 兼容协议中的一个 code */
    public @NotNull String getProtocolCode() {
        return Objects.requireNonNull(protocolCode);
    }

    /** @param protocolCode Product 兼容协议中的一个 code */
    public void setProtocolCode(@Nullable String protocolCode) {
        this.protocolCode = protocolCode;
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

    /** @return Product 允许接入方式的子集；间接 Device 为空 */
    public @NotNull Set<String> getAccessMethods() {
        return Set.copyOf(Objects.requireNonNull(accessMethods));
    }

    /** @param accessMethods Product 允许接入方式的子集；间接 Device 传空集合 */
    public void setAccessMethods(@Nullable Set<String> accessMethods) {
        this.accessMethods = accessMethods == null ? null : Set.copyOf(accessMethods);
    }

    /** @return 可选 X.509 公钥证书 DER 的 Base64 文本 */
    public @Nullable String getCertificateDerBase64() {
        return certificateDerBase64;
    }

    /** @param certificateDerBase64 可选 X.509 公钥证书 DER 的 Base64 文本 */
    public void setCertificateDerBase64(@Nullable String certificateDerBase64) {
        this.certificateDerBase64 = certificateDerBase64;
    }
}
