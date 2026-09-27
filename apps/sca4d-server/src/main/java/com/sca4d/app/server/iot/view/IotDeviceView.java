package com.sca4d.app.server.iot.view;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.app.server.iot.IotDeviceDetails;
import com.sca4d.app.server.iot.IotDeviceProfile;
import com.sca4d.app.server.iot.IotProfileStatus;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductSource;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** 返回 Sca4d Server 管理 Device 所需的公共 Profile、连接身份和启停状态。 */
@SuppressWarnings("unused")
public final class IotDeviceView {

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

    private final @Nullable String connectionIdentityType;

    private final @Nullable String connectionIdentity;

    private final @NotNull @Unmodifiable List<String> accessMethods;

    private final @NotNull IotProfileStatus status;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    /** 从公共 Device、连接身份和应用 Profile 创建 HTTP 视图。 */
    private IotDeviceView(
            @NotNull Device device,
            @NotNull IotDeviceProfile profile) {
        this.id = device.id();
        this.productSource = device.product().source();
        this.productCode = device.product().code();
        OptionalLong databaseProductId = device.product().databaseProductId();
        this.productId = databaseProductId.isPresent() ? databaseProductId.getAsLong() : null;
        this.code = device.code();
        this.serialNumber = device.serialNumber();
        this.displayName = device.displayName();
        this.remark = device.remark();
        this.thingModelVersion = device.thingModelVersion();
        this.bindingGeneration = device.bindingGeneration();
        this.protocolCode = device.protocolCode();
        DeviceConnectionBinding connection = device.connection().orElse(null);
        this.connectionProtocol = connection == null ? null : connection.definition().protocol().code();
        this.connectionIdentityType = connection == null ? null : connection.definition().identifierType();
        this.connectionIdentity = connection == null ? null : connection.identifierValue();
        this.accessMethods = device.accessMethods()
                .stream()
                .map(method -> method.code().format(COLON))
                .sorted()
                .toList();
        this.status = profile.status();
        this.createdAt = profile.createdAt();
        this.updatedAt = profile.updatedAt();
    }

    /** @return 合并公共 Device、连接身份与 App Profile 后的管理视图 */
    @CheckReturnValue
    public static @NotNull IotDeviceView from(
            @NotNull Device device,
            @NotNull IotDeviceProfile profile) {
        Device checkedDevice = Objects.requireNonNull(device, "Device 不能为空");
        IotDeviceProfile checkedProfile = Objects.requireNonNull(profile, "Device App Profile 不能为空");
        if (checkedDevice.id() != checkedProfile.deviceId()) {
            throw new IllegalArgumentException("Device 与 App Profile 标识不一致");
        }
        return new IotDeviceView(
                checkedDevice,
                checkedProfile);
    }

    /** @return 应用 Device 查询结果对应的 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotDeviceView from(@NotNull IotDeviceDetails details) {
        IotDeviceDetails checked = Objects.requireNonNull(details, "Device 应用查询结果不能为空");
        return from(
                checked.device(),
                checked.profile());
    }

    /** @return Device 标识 */
    public long getId() {
        return id;
    }

    /** @return Product 定义来源 */
    public @NotNull ProductSource getProductSource() {
        return productSource;
    }

    /** @return Product 稳定 code */
    public @NotNull String getProductCode() {
        return productCode;
    }

    /** @return 数据库 Product 标识；代码 Product 时为空 */
    public @Nullable Long getProductId() {
        return productId;
    }

    /** @return Product 内唯一 Device code */
    public @NotNull String getCode() {
        return code;
    }

    /** @return 可选 Product 内唯一序列号 */
    public @Nullable String getSerialNumber() {
        return serialNumber;
    }

    /** @return 展示名称 */
    public @NotNull String getDisplayName() {
        return displayName;
    }

    /** @return 可选管理备注 */
    public @Nullable String getRemark() {
        return remark;
    }

    /** @return 当前物模型版本 */
    public int getThingModelVersion() {
        return thingModelVersion;
    }

    /** @return 当前物模型绑定代数 */
    public long getBindingGeneration() {
        return bindingGeneration;
    }

    /** @return 当前设备协议 code */
    public @NotNull String getProtocolCode() {
        return protocolCode;
    }

    /** @return 当前连接协议 code */
    public @Nullable String getConnectionProtocol() {
        return connectionProtocol;
    }

    /** @return 当前直接连接使用的身份类型 */
    public @Nullable String getConnectionIdentityType() {
        return connectionIdentityType;
    }

    /** @return 当前登记的公开连接身份值 */
    public @Nullable String getConnectionIdentity() {
        return connectionIdentity;
    }

    /** @return Device 允许的接入方式 code */
    public @NotNull @Unmodifiable List<String> getAccessMethods() {
        return List.copyOf(accessMethods);
    }

    /** @return Sca4d Server 启停状态 */
    public @NotNull IotProfileStatus getStatus() {
        return status;
    }

    /** @return App Profile 创建时间 */
    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }

    /** @return App Profile 最近修改时间 */
    public @NotNull Instant getUpdatedAt() {
        return updatedAt;
    }
}
