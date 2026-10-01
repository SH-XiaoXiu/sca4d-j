package com.sca4d.iot.device;

import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductSelector;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 保存创建 Device 时总是一起校验和写入的公共 Profile 数据。
 */
public class DeviceRegistration {

    private final @Nullable DeviceConnectionBinding connection;

    private final @NotNull ProductSelector product;

    private final @NotNull Set<DeviceAccessMethod> accessMethods;

    private final @NotNull String code;

    private final @NotNull String displayName;

    private final @NotNull String protocolCode;

    private final @Nullable String remark;

    private final @Nullable String serialNumber;

    private final int thingModelVersion;

    /** 创建完整 Device 登记数据。 */
    @SuppressWarnings("ParameterNumber")
    public DeviceRegistration(
            @NotNull ProductSelector product,
            @NotNull String code,
            @Nullable String serialNumber,
            @NotNull String displayName,
            @Nullable String remark,
            int thingModelVersion,
            @NotNull String protocolCode,
            @Nullable DeviceConnectionBinding connection,
            @NotNull Set<DeviceAccessMethod> accessMethods) {
        this.product = Objects.requireNonNull(product, "Device Product 选择不能为空");
        this.code = code;
        this.serialNumber = serialNumber;
        this.displayName = displayName;
        this.remark = remark;
        this.thingModelVersion = thingModelVersion;
        this.protocolCode = protocolCode;
        this.connection = connection;
        this.accessMethods = Set.copyOf(Objects.requireNonNull(accessMethods, "Device 接入方式不能为空"));
    }

    /**
     * @return Product 来源选择
     */
    public @NotNull ProductSelector product() {
        return product;
    }

    /**
     * @return Device code
     */
    public @NotNull String code() {
        return code;
    }

    /**
     * @return 可选序列号
     */
    public @Nullable String serialNumber() {
        return serialNumber;
    }

    /**
     * @return 展示名称
     */
    public @NotNull String displayName() {
        return displayName;
    }

    /**
     * @return 可选备注
     */
    public @Nullable String remark() {
        return remark;
    }

    /**
     * @return 物模型版本
     */
    public int thingModelVersion() {
        return thingModelVersion;
    }

    /**
     * @return 协议 code
     */
    public @NotNull String protocolCode() {
        return protocolCode;
    }

    /** @return 可选直接连接绑定；间接 Device 为空 */
    public @NotNull Optional<DeviceConnectionBinding> connection() {
        return Optional.ofNullable(connection);
    }

    /**
     * @return Device 接入方式子集
     */
    public @NotNull @Unmodifiable Set<DeviceAccessMethod> accessMethods() {
        return accessMethods;
    }

}
