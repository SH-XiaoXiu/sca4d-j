package com.sca4d.iot.persistence;

import com.sca4d.iot.api.ProductSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 保存 Mapper 查询得到的 Device 公共 Profile 标量字段。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class DeviceState {
    private final long id;

    private final @NotNull ProductSource productSource;

    private final @NotNull String productCode;

    private final @Nullable Long productId;

    private final @NotNull String code;

    private final @Nullable String serialNumber;

    private final @NotNull String displayName;

    private final @Nullable String remark;

    private final int thingModelVersion;

    private final long bindingGeneration;

    private final @NotNull String protocolCode;

    private final @Nullable String connectionProtocol;

    private final @Nullable String connectionIdentifierType;

    private final @Nullable String connectionIdentifierValue;

    /** 创建完整 Device 持久化状态。 */
    @SuppressWarnings("ParameterNumber")
    public DeviceState(
            long id,
            @NotNull ProductSource productSource,
            @NotNull String productCode,
            @Nullable Long productId,
            @NotNull String code,
            @Nullable String serialNumber,
            @NotNull String displayName,
            @Nullable String remark,
            int thingModelVersion,
            long bindingGeneration,
            @NotNull String protocolCode,
            @Nullable String connectionProtocol,
            @Nullable String connectionIdentifierType,
            @Nullable String connectionIdentifierValue) {
        this.id = id;
        this.productSource = productSource;
        this.productCode = productCode;
        this.productId = productId;
        this.code = code;
        this.serialNumber = serialNumber;
        this.displayName = displayName;
        this.remark = remark;
        this.thingModelVersion = thingModelVersion;
        this.bindingGeneration = bindingGeneration;
        this.protocolCode = protocolCode;
        this.connectionProtocol = connectionProtocol;
        this.connectionIdentifierType = connectionIdentifierType;
        this.connectionIdentifierValue = connectionIdentifierValue;
    }

    /** @return Device 标识 */
    public long id() {
        return id;
    }

    /** @return Product 定义来源 */
    public @NotNull ProductSource productSource() {
        return productSource;
    }

    /** @return Product 稳定 code */
    public @NotNull String productCode() {
        return productCode;
    }

    /** @return 数据库 Product 标识；代码 Product 时为空 */
    public @Nullable Long productId() {
        return productId;
    }

    /** @return Device code */
    public @NotNull String code() {
        return code;
    }

    /** @return 可选序列号 */
    public @Nullable String serialNumber() {
        return serialNumber;
    }

    /** @return 展示名称 */
    public @NotNull String displayName() {
        return displayName;
    }

    /** @return 可选备注 */
    public @Nullable String remark() {
        return remark;
    }

    /** @return 物模型版本 */
    public int thingModelVersion() {
        return thingModelVersion;
    }

    /** @return 绑定代数 */
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /** @return 协议 code */
    public @NotNull String protocolCode() {
        return protocolCode;
    }

    /** @return 直接连接协议；间接 Device 时为空 */
    public @Nullable String connectionProtocol() {
        return connectionProtocol;
    }

    /** @return 直接连接身份类型；间接 Device 时为空 */
    public @Nullable String connectionIdentifierType() {
        return connectionIdentifierType;
    }

    /** @return 直接连接身份值；间接 Device 时为空 */
    public @Nullable String connectionIdentifierValue() {
        return connectionIdentifierValue;
    }

}
