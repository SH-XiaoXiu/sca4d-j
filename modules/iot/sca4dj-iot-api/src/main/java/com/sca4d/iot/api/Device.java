package com.sca4d.iot.api;

import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 保存 DEVICE Subject 对应的公共 IoT Profile。 */
public final class Device {

    private final long id;

    private final @NotNull ProductBinding product;

    private final @NotNull String code;

    private final @Nullable String serialNumber;

    private final @NotNull String displayName;

    private final @Nullable String remark;

    private final int thingModelVersion;

    private final long bindingGeneration;

    private final @NotNull String protocolCode;

    private final @Nullable DeviceConnectionBinding connection;

    private final @NotNull Set<DeviceAccessMethod> accessMethods;

    /** 创建已经通过 Product 约束校验的 Device 快照。 */
    @SuppressWarnings("ParameterNumber")
    public Device(
            long id,
            @NotNull ProductBinding product,
            @NotNull String code,
            @Nullable String serialNumber,
            @NotNull String displayName,
            @Nullable String remark,
            int thingModelVersion,
            long bindingGeneration,
            @NotNull String protocolCode,
            @Nullable DeviceConnectionBinding connection,
            @NotNull Collection<DeviceAccessMethod> accessMethods) {
        if (id <= 0 || thingModelVersion <= 0 || bindingGeneration <= 0) {
            throw new IllegalArgumentException("Device、物模型版本和绑定代数 必须为正数");
        }
        this.id = id;
        this.product = Objects.requireNonNull(product, "Device Product 绑定不能为空");
        this.code = TextUtils.requireText(code, "Device code");
        this.serialNumber = serialNumber == null
                ? null
                : TextUtils.requireText(serialNumber, "Device serialNumber");
        this.displayName = TextUtils.requireText(displayName, "Device 展示名称");
        this.remark = remark == null ? null : TextUtils.requireText(remark, "Device 备注");
        this.thingModelVersion = thingModelVersion;
        this.bindingGeneration = bindingGeneration;
        this.protocolCode = TextUtils.requireText(protocolCode, "Device 协议 code");
        this.connection = connection;
        this.accessMethods = Set.copyOf(Objects.requireNonNull(accessMethods, "Device 接入方式不能为空"));
        if ((this.connection == null) != this.accessMethods.isEmpty()) {
            throw new IllegalArgumentException("Device 直接连接和接入方式必须同时存在或同时为空");
        }
    }

    /** @return 与 Subject 标识相同的 Device 标识 */
    public long id() {
        return id;
    }

    /** @return 所属 Product 来源、code 和可选数据库标识 */
    public @NotNull ProductBinding product() {
        return product;
    }

    /** @return Product 内唯一 Device code */
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

    /** @return 当前显式绑定的物模型版本 */
    public int thingModelVersion() {
        return thingModelVersion;
    }

    /** @return 每次切换物模型时递增的绑定代数 */
    public long bindingGeneration() {
        return bindingGeneration;
    }

    /** @return 当前 Java 协议实现 code */
    public @NotNull String protocolCode() {
        return protocolCode;
    }

    /** @return 可选直接连接绑定；网关中继等间接 Device 为空 */
    public @NotNull Optional<DeviceConnectionBinding> connection() {
        return Optional.ofNullable(connection);
    }

    /** @return Device 当前允许使用的认证接入方式；没有直接连接时为空 */
    public @NotNull @Unmodifiable Set<DeviceAccessMethod> accessMethods() {
        return accessMethods;
    }
}
