package com.sca4d.app.server.graphql.iot;

import com.sca4d.app.server.openapi.iot.OpenApiIotDeviceResponse;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 保存 IoT GraphQL 开放查询返回的稳定字段。 */
public class IotGraphQlDevice {

    private final @NotNull String id;

    private final @NotNull String productCode;

    private final @NotNull String code;

    private final @NotNull String displayName;

    private final @NotNull String protocolCode;

    private final @NotNull String status;

    private final @NotNull List<@NotNull String> accessMethods;

    /** 从 OpenAPI 的稳定 Device 转换为 GraphQL 返回对象。 */
    public IotGraphQlDevice(@NotNull OpenApiIotDeviceResponse source) {
        OpenApiIotDeviceResponse checked = java.util.Objects.requireNonNull(source, "Device 响应不能为空");
        this.id = checked.getId();
        this.productCode = checked.getProductCode();
        this.code = checked.getCode();
        this.displayName = checked.getDisplayName();
        this.protocolCode = checked.getProtocolCode();
        this.status = checked.getStatus().name();
        this.accessMethods = checked.getAccessMethods();
    }

    /** @return Device 十进制标识 */
    public @NotNull String getId() {
        return id;
    }

    /** @return Product code */
    public @NotNull String getProductCode() {
        return productCode;
    }

    /** @return Product 内 Device code */
    public @NotNull String getCode() {
        return code;
    }

    /** @return Device 展示名称 */
    public @NotNull String getDisplayName() {
        return displayName;
    }

    /** @return 设备协议 code */
    public @NotNull String getProtocolCode() {
        return protocolCode;
    }

    /** @return 当前设备状态 */
    public @NotNull String getStatus() {
        return status;
    }

    /** @return 允许的设备接入方式 */
    public @NotNull List<@NotNull String> getAccessMethods() {
        return accessMethods;
    }
}
