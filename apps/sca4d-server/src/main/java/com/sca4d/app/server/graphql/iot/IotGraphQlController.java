package com.sca4d.app.server.graphql.iot;

import com.sca4d.app.server.iot.IotOperations;
import com.sca4d.app.server.openapi.iot.OpenApiIotDeviceResponse;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.IotApplicationService;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.spring.config.ConditionalOnCapability;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iot.api.IotCapabilities;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Objects;

/** 提供具体应用的 IoT GraphQL 开放查询。 */
@Controller
@ConditionalOnCapability(IotCapabilities.API)
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class IotGraphQlController {

    private final @NotNull IotApplicationService iot;

    /** 创建 IoT GraphQL Controller。 */
    public IotGraphQlController(@NotNull IotApplicationService iot) {
        this.iot = Objects.requireNonNull(iot, "IoT 应用服务不能为空");
    }

    /** 按 Device 标识读取对外公开的稳定资料。 */
    @QueryMapping
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull IotGraphQlDevice iotDevice(@Argument long deviceId) {
        OpenApiIotDeviceResponse response = OpenApiIotDeviceResponse.from(iot.deviceById(deviceId));
        return new IotGraphQlDevice(response);
    }
}
