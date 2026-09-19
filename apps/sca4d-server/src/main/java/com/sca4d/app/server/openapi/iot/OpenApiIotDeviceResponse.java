package com.sca4d.app.server.openapi.iot;

import com.sca4d.app.server.iot.IotDeviceDetails;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** 返回第三方程序读取 IoT Device 时需要的稳定字段，不复用管理端 HTTP 视图。 */
@Schema(name = "IotDevice", description = "开放 API 返回的 IoT Device 资料、连接标识和启停状态。")
public final class OpenApiIotDeviceResponse {

    private final @NotNull String id;

    private final @NotNull OpenApiIotProductSource productSource;

    private final @NotNull String productCode;

    private final @Nullable String productId;

    private final @NotNull String code;

    private final @Nullable String serialNumber;

    private final @NotNull String displayName;

    private final @Nullable String remark;

    private final int thingModelVersion;

    private final @NotNull String bindingGeneration;

    private final @NotNull String protocolCode;

    private final @Nullable String connectionProtocol;

    private final @Nullable String connectionIdentityType;

    private final @Nullable String connectionIdentity;

    private final @NotNull @Unmodifiable List<String> accessMethods;

    private final @NotNull OpenApiIotDeviceStatus status;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    /** 从应用 Device 查询结果创建开放 API 响应。 */
    private OpenApiIotDeviceResponse(@NotNull IotDeviceDetails details) {
        Device device = details.device();
        this.id = Long.toString(device.id());
        this.productSource = OpenApiIotProductSource.from(device.product().source());
        this.productCode = device.product().code();
        OptionalLong databaseProductId = device.product().databaseProductId();
        this.productId = databaseProductId.isPresent() ? Long.toString(databaseProductId.getAsLong()) : null;
        this.code = device.code();
        this.serialNumber = device.serialNumber();
        this.displayName = device.displayName();
        this.remark = device.remark();
        this.thingModelVersion = device.thingModelVersion();
        this.bindingGeneration = Long.toString(device.bindingGeneration());
        this.protocolCode = device.protocolCode();
        DeviceConnectionBinding connection = device.connection().orElse(null);
        this.connectionProtocol = connection == null ? null : connection.definition().protocol().code();
        this.connectionIdentityType = connection == null ? null : connection.definition().identifierType();
        this.connectionIdentity = connection == null ? null : connection.identifierValue();
        this.accessMethods = device.accessMethods()
                .stream()
                .map(DeviceAccessMethod::code)
                .sorted()
                .toList();
        this.status = OpenApiIotDeviceStatus.from(details.profile().status());
        this.createdAt = details.profile().createdAt();
        this.updatedAt = details.profile().updatedAt();
    }

    /**
     * 把内部应用查询结果转换为开放 API 响应。
     *
     * @param details Device、连接标识和应用 Profile
     * @return 字段已经转换为开放 API 类型的响应
     */
    @CheckReturnValue
    public static @NotNull OpenApiIotDeviceResponse from(@NotNull IotDeviceDetails details) {
        return new OpenApiIotDeviceResponse(Objects.requireNonNull(details, "Device 查询结果不能为空"));
    }

    /** @return Device 十进制标识 */
    @Schema(description = "Device 十进制标识。", example = "1057296481730560", pattern = "^[1-9][0-9]*$")
    public @NotNull String getId() {
        return id;
    }

    /** @return Product 定义来源 */
    @Schema(description = "Product 定义的维护位置。", example = "DATABASE")
    public @NotNull OpenApiIotProductSource getProductSource() {
        return productSource;
    }

    /** @return Product 稳定 code */
    @Schema(description = "Device 所属 Product 的稳定 code。", example = "cold-chain-sensor")
    public @NotNull String getProductCode() {
        return productCode;
    }

    /** @return 数据库 Product 十进制标识；代码 Product 时为空 */
    @Schema(description = "数据库 Product 十进制标识；productSource 为 CODE 时为空。", example = "1057296406233088", pattern = "^[1-9][0-9]*$", nullable = true)
    public @Nullable String getProductId() {
        return productId;
    }

    /** @return Product 内唯一的 Device code */
    @Schema(description = "Product 内唯一的 Device code。", example = "freezer-01")
    public @NotNull String getCode() {
        return code;
    }

    /** @return 可选序列号 */
    @Schema(description = "Product 内唯一的可选序列号。", example = "SN-20260815-0001", nullable = true)
    public @Nullable String getSerialNumber() {
        return serialNumber;
    }

    /** @return 展示名称 */
    @Schema(description = "Device 展示名称。", example = "一号冷柜温湿度传感器")
    public @NotNull String getDisplayName() {
        return displayName;
    }

    /** @return 可选管理备注 */
    @Schema(description = "Device 的可选备注。", example = "仓库 A 区", nullable = true)
    public @Nullable String getRemark() {
        return remark;
    }

    /** @return 当前物模型版本 */
    @Schema(description = "当前显式绑定的物模型版本。", example = "3", minimum = "1")
    public int getThingModelVersion() {
        return thingModelVersion;
    }

    /** @return 当前物模型绑定代数的十进制字符串 */
    @Schema(description = "每次切换物模型时递增的绑定代数。", example = "7", pattern = "^[1-9][0-9]*$")
    public @NotNull String getBindingGeneration() {
        return bindingGeneration;
    }

    /** @return 当前设备协议 code */
    @Schema(description = "当前 Java 设备协议的稳定 code。", example = "standard-climate")
    public @NotNull String getProtocolCode() {
        return protocolCode;
    }

    /** @return 当前连接协议 code */
    @Schema(description = "当前网络连接协议的稳定 code；间接 Device 为空。", example = "mqtt", nullable = true)
    public @Nullable String getConnectionProtocol() {
        return connectionProtocol;
    }

    /** @return 当前直接连接使用的身份类型 */
    @Schema(description = "当前连接使用的公开身份类型；间接 Device 为空。", example = "mqtt-client-id", nullable = true)
    public @Nullable String getConnectionIdentityType() {
        return connectionIdentityType;
    }

    /** @return 当前登记的公开连接标识值 */
    @Schema(description = "当前登记的公开连接身份值；间接 Device 为空。", example = "freezer-01", nullable = true)
    public @Nullable String getConnectionIdentity() {
        return connectionIdentity;
    }

    /** @return Device 允许的接入方式 code */
    @Schema(description = "Device 允许使用的接入方式 code。", example = "[\"HMAC\", \"SHARED_SECRET\"]")
    public @NotNull @Unmodifiable List<String> getAccessMethods() {
        return List.copyOf(accessMethods);
    }

    /** @return Device 当前启停状态 */
    @Schema(description = "Device 当前是否允许认证、连接和交换消息。", example = "ENABLED")
    public @NotNull OpenApiIotDeviceStatus getStatus() {
        return status;
    }

    /** @return Device App Profile 创建时间 */
    @Schema(description = "Device 在当前应用中的创建时间。", example = "2026-08-01T08:30:00Z")
    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }

    /** @return Device App Profile 最近修改时间 */
    @Schema(description = "Device 在当前应用中的最近修改时间。", example = "2026-08-15T02:30:00Z")
    public @NotNull Instant getUpdatedAt() {
        return updatedAt;
    }
}
