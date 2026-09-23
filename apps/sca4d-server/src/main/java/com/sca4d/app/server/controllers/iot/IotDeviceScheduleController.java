package com.sca4d.app.server.controllers.iot;

import com.sca4d.app.server.iot.IotOperations;
import com.sca4d.app.server.iot.request.IotDeviceScheduleRequest;
import com.sca4d.app.server.iot.request.IotDeviceScheduleStatusRequest;
import com.sca4d.app.server.iot.view.IotDeviceScheduleExecutionView;
import com.sca4d.app.server.iot.view.IotDeviceScheduleView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.schedule.DeviceSchedule;
import com.sca4d.iot.api.schedule.DeviceScheduleQueryService;
import com.sca4d.iot.api.schedule.DeviceScheduleService;
import com.sca4d.iot.api.schedule.DeviceScheduleTrigger;
import com.sca4d.iot.api.schedule.DeviceScheduledCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** 提供 Sca4d Server 的 Device command 高精度定时规则和执行结果接口。 */
@RestController
@ConditionalOnProperty(prefix = "app.iot.capabilities", name = "api", havingValue = "true", matchIfMissing = true)
@RequestMapping("/iot/devices/{deviceId}/schedules")
@Tag(name = "IoT Device Schedules")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class IotDeviceScheduleController {

    private final @NotNull DeviceScheduleService schedules;

    private final @NotNull DeviceScheduleQueryService queries;

    /** 创建只处理 App HTTP、权限和 View 转换的设备定时 Controller。 */
    public IotDeviceScheduleController(
            @NotNull DeviceScheduleService schedules,
            @NotNull DeviceScheduleQueryService queries) {
        this.schedules = Objects.requireNonNull(schedules, "Device 定时服务不能为空");
        this.queries = Objects.requireNonNull(queries, "Device 定时查询服务不能为空");
    }

    /** @return 新建并登记下一次触发时间的设备定时规则 */
    @PostMapping
    @Operation(summary = "创建设备 command 定时规则")
    @RequirePermission(operation = IotOperations.DEVICES_OPERATE_VALUE)
    public @NotNull ApiResponse<IotDeviceScheduleView> create(
            @PathVariable long deviceId,
            @Valid @RequestBody @NotNull IotDeviceScheduleRequest request) {
        DeviceSchedule created = schedules.create(
                deviceId,
                command(request),
                trigger(request),
                Duration.ofMillis(request.getValidForMillis()));
        return ApiResponse.created(
                IotDeviceScheduleView.from(created),
                URI.create("/iot/devices/" + deviceId + "/schedules/" + created.scheduleId()));
    }

    /** @return Device 的定时规则页 */
    @GetMapping
    @Operation(summary = "查询设备定时规则")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<Page<IotDeviceScheduleView>> findAll(
            @PathVariable long deviceId,
            @NotNull PageRequest page) {
        return ApiResponse.ok(queries.schedules(deviceId, page).map(IotDeviceScheduleView::from));
    }

    /** @return 指定 Device 的定时规则 */
    @GetMapping("/{scheduleId}")
    @Operation(summary = "查询设备定时规则详情")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<IotDeviceScheduleView> get(
            @PathVariable long deviceId,
            @PathVariable long scheduleId) {
        return ApiResponse.ok(IotDeviceScheduleView.from(ownedSchedule(deviceId, scheduleId)));
    }

    /** @return 按乐观版本完整替换并恢复 ACTIVE 的定时规则 */
    @PutMapping("/{scheduleId}")
    @Operation(summary = "完整替换设备定时规则")
    @RequirePermission(operation = IotOperations.DEVICES_OPERATE_VALUE)
    public @NotNull ApiResponse<IotDeviceScheduleView> replace(
            @PathVariable long deviceId,
            @PathVariable long scheduleId,
            @Valid @RequestBody @NotNull IotDeviceScheduleRequest request) {
        validateVersion(request.getVersion());
        ownedSchedule(deviceId, scheduleId);
        return ApiResponse.ok(
                IotDeviceScheduleView.from(
                        schedules.replace(
                                scheduleId,
                                request.getVersion(),
                                command(request),
                                trigger(request),
                                Duration.ofMillis(request.getValidForMillis()))));
    }

    /** @return 按乐观版本暂停或恢复后的定时规则 */
    @PatchMapping("/{scheduleId}/status")
    @Operation(summary = "暂停或恢复设备定时规则")
    @RequirePermission(operation = IotOperations.DEVICES_OPERATE_VALUE)
    public @NotNull ApiResponse<IotDeviceScheduleView> changeStatus(
            @PathVariable long deviceId,
            @PathVariable long scheduleId,
            @Valid @RequestBody @NotNull IotDeviceScheduleStatusRequest request) {
        validateVersion(request.getVersion());
        ownedSchedule(deviceId, scheduleId);
        return ApiResponse.ok(
                IotDeviceScheduleView.from(
                        schedules.changeStatus(
                                scheduleId,
                                request.getVersion(),
                                request.getStatus())));
    }

    /** 永久取消规则；已经生成的下行继续遵守自己的状态机。 */
    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "取消设备定时规则")
    @RequirePermission(operation = IotOperations.DEVICES_OPERATE_VALUE)
    public @NotNull ApiResponse<Void> cancel(
            @PathVariable long deviceId,
            @PathVariable long scheduleId,
            @RequestParam long version) {
        validateVersion(version);
        ownedSchedule(deviceId, scheduleId);
        schedules.cancel(scheduleId, version);
        return ApiResponse.ok();
    }

    /** @return 一个规则每个计划时间的下行生成结果页 */
    @GetMapping("/{scheduleId}/executions")
    @Operation(summary = "查询设备定时执行结果")
    @RequirePermission(operation = IotOperations.DEVICES_READ_VALUE)
    public @NotNull ApiResponse<Page<IotDeviceScheduleExecutionView>> executions(
            @PathVariable long deviceId,
            @PathVariable long scheduleId,
            @NotNull PageRequest page) {
        ownedSchedule(deviceId, scheduleId);
        return ApiResponse.ok(queries.executions(scheduleId, page).map(IotDeviceScheduleExecutionView::from));
    }

    /** 把 HTTP command 字段转换成模块稳定类型。 */
    private static @NotNull DeviceScheduledCommand command(
            @NotNull IotDeviceScheduleRequest request) {
        return new DeviceScheduledCommand(request.getCommandCode(), request.getInputs());
    }

    /** 要求单次时间或 Cron 加时区正好提供一种。 */
    private static @NotNull DeviceScheduleTrigger trigger(
            @NotNull IotDeviceScheduleRequest request) {
        Instant executeAt = request.getExecuteAt();
        String cronExpression = request.getCronExpression();
        String zoneId = request.getZoneId();
        boolean once = executeAt != null;
        boolean cron = cronExpression != null
                && !cronExpression.isBlank()
                && zoneId != null
                && !zoneId.isBlank();
        if (once == cron) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        if (once) {
            if (cronExpression != null || zoneId != null) {
                throw new BusinessException(IotErrorCode.INVALID_INPUT);
            }
            return DeviceScheduleTrigger.once(executeAt);
        }
        try {
            return DeviceScheduleTrigger.cron(cronExpression, ZoneId.of(zoneId));
        } catch (RuntimeException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
    }

    /** 读取规则并校验它属于路径中的 Device。 */
    private @NotNull DeviceSchedule ownedSchedule(long deviceId, long scheduleId) {
        DeviceSchedule schedule = queries.loadById(scheduleId);
        if (schedule.deviceId() != deviceId) {
            throw new BusinessException(IotErrorCode.STATE_CONFLICT);
        }
        return schedule;
    }

    /** 要求客户端提交从查询结果取得的非负乐观版本。 */
    private static void validateVersion(long version) {
        if (version < 0) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
    }
}
