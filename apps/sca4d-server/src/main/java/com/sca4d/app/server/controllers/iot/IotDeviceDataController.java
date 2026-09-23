package com.sca4d.app.server.controllers.iot;

import com.sca4d.app.server.iot.IotOperations;
import com.sca4d.app.server.iot.view.IotAcknowledgementView;
import com.sca4d.app.server.iot.view.IotDeviceEventView;
import com.sca4d.app.server.iot.view.IotDeviceRuntimeView;
import com.sca4d.app.server.iot.view.IotDownlinkMessageView;
import com.sca4d.app.server.iot.view.IotTelemetryView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.IotDeviceRuntimeService;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iot.api.query.DeviceHistoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 提供 Sca4d Server 的 Device 当前投影、业务历史和可选高频历史查询接口。 */
@RestController
@ConditionalOnProperty(prefix = "app.iot.capabilities", name = "api", havingValue = "true", matchIfMissing = true)
@RequestMapping("/iot/devices/{deviceId}")
@Tag(name = "IoT Device Data")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class IotDeviceDataController {

    private final @NotNull IotDeviceRuntimeService runtime;

    private final @NotNull DeviceHistoryQueryService history;

    /** 创建只把模块查询结果转换为 App HTTP View 的 Controller。 */
    public IotDeviceDataController(
            @NotNull IotDeviceRuntimeService runtime,
            @NotNull DeviceHistoryQueryService history) {
        this.runtime = Objects.requireNonNull(runtime, "Device 运行态应用服务不能为空");
        this.history = Objects.requireNonNull(history, "Device 历史查询服务不能为空");
    }

    /** @return Device 当前连接、reported 和 desired 权威投影 */
    @GetMapping("/runtime")
    @Operation(summary = "查询 Device 当前运行投影")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<IotDeviceRuntimeView> runtime(@PathVariable long deviceId) {
        return ApiResponse.ok(IotDeviceRuntimeView.from(runtime.read(deviceId)));
    }

    /** @return Device 下行消息页 */
    @GetMapping("/downlinks")
    @Operation(summary = "查询 Device 下行历史")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<Page<IotDownlinkMessageView>> downlinks(
            @PathVariable long deviceId,
            @NotNull PageRequest page) {
        return ApiResponse.ok(history.downlinks(deviceId, page).map(IotDownlinkMessageView::from));
    }

    /** @return Device 业务 ACK 页 */
    @GetMapping("/acknowledgements")
    @Operation(summary = "查询 Device ACK 历史")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<Page<IotAcknowledgementView>> acknowledgements(
            @PathVariable long deviceId,
            @NotNull PageRequest page) {
        return ApiResponse.ok(history.acknowledgements(deviceId, page).map(IotAcknowledgementView::from));
    }

    /** @return Device 离散事件页 */
    @GetMapping("/events")
    @Operation(summary = "查询 Device 事件历史")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<Page<IotDeviceEventView>> events(
            @PathVariable long deviceId,
            @NotNull PageRequest page) {
        return ApiResponse.ok(history.events(deviceId, page).map(IotDeviceEventView::from));
    }

    /** @return 半开时间范围内的属性 Telemetry */
    @GetMapping("/telemetry")
    @Operation(summary = "查询 Device Telemetry")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<List<IotTelemetryView>> telemetry(
            @PathVariable long deviceId,
            @RequestParam @NotNull String propertyCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @NotNull Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @NotNull Instant to,
            @RequestParam(defaultValue = "1000") int limit) {
        return ApiResponse.ok(
                history.telemetry(deviceId, propertyCode, from, to, limit)
                        .stream()
                        .map(IotTelemetryView::from)
                        .toList());
    }

}
