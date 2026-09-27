package com.sca4d.app.server.controllers.iot;

import com.sca4d.app.server.iot.IotOperations;
import com.sca4d.app.server.openapi.iot.OpenApiIotDeviceResponse;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.IotApplicationService;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.openapi.web.annotation.OpenApiRoute;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 声明具体应用对第三方 OpenAPI Client 公开的 IoT Tool 路由。 */
@RestController
@ConditionalOnCapability(IotCapabilities.API)
@Tag(name = "IoT 设备", description = "供第三方程序读取 Sca4d Server 中已经登记的 IoT 设备。")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class IotOpenApiController {

    private final @NotNull IotApplicationService iot;

    /** 创建 IoT OpenAPI Controller。 */
    public IotOpenApiController(@NotNull IotApplicationService iot) {
        this.iot = Objects.requireNonNull(iot, "IoT 应用服务不能为空");
    }

    /**
     * 读取指定 IoT Device 的公开资料、连接标识和启停状态。
     *
     * @param deviceId 十进制 Device 标识
     * @return 独立于管理端响应类型的开放 API Device 响应
     */
    @OpenApiRoute(method = RequestMethod.GET, path = "/openapi/iot/devices/{deviceId}", toolGroup = "iot", toolName = "getDevice", operation = IotOperations.DEVICES_READ_VALUE)
    @Operation(summary = "查询 IoT 设备", description = "按 Device 标识读取设备资料、Product 绑定、连接标识、接入方式和启停状态。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "找到设备并返回当前资料", useReturnTypeSchema = true, content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "device", summary = "数据库 Product 下启用的设备", value = """
                    {
                      "code": "0",
                      "messageKey": null,
                      "messageArgs": [],
                      "message": null,
                      "data": {
                        "id": "1057296481730560",
                        "productSource": "DATABASE",
                        "productCode": "cold-chain-sensor",
                        "productId": "1057296406233088",
                        "code": "freezer-01",
                        "serialNumber": "SN-20260815-0001",
                        "displayName": "一号冷柜温湿度传感器",
                        "remark": "仓库 A 区",
                        "thingModelVersion": 3,
                        "bindingGeneration": "7",
                        "protocolCode": "standard-climate",
                        "connectionProtocol": "mqtt",
                        "connectionIdentityType": "mqtt-client-id",
                        "connectionIdentity": "freezer-01",
                        "accessMethods": ["HMAC", "SHARED_SECRET"],
                        "status": "ENABLED",
                        "createdAt": "2026-08-01T08:30:00Z",
                        "updatedAt": "2026-08-15T02:30:00Z"
                      },
                      "callId": "01K2N8Y6T4FJ7N8Z5P3Q6R9S0A",
                      "timestamp": "2026-08-15T02:30:00Z"
                    }
                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Device 标识不存在", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/OpenApiError"), examples = @ExampleObject(name = "deviceNotFound", summary = "设备不存在", value = """
                    {
                      "code": "2901",
                      "messageKey": "error.iot.device-not-found",
                      "messageArgs": [],
                      "message": "IoT Device 不存在",
                      "data": null,
                      "callId": "01K2N8Y6T4FJ7N8Z5P3Q6R9S0A",
                      "timestamp": "2026-08-15T02:30:00Z"
                    }
                    """)))
    })
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<OpenApiIotDeviceResponse> get(
            @Parameter(description = "Device 的十进制标识。", required = true, schema = @Schema(type = "string", example = "1057296481730560", pattern = "^[1-9][0-9]*$")) @PathVariable long deviceId) {
        return ApiResponse.ok(OpenApiIotDeviceResponse.from(iot.deviceById(deviceId)));
    }
}
