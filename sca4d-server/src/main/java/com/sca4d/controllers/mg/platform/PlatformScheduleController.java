package com.sca4d.controllers.mg.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.common.web.PageData;
import com.sca4d.common.web.PageQuery;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.platform.audit.PlatformAuditOperations;
import com.sca4d.schedule.Schedule;
import com.sca4d.schedule.ScheduleErrorCode;
import com.sca4d.schedule.ScheduleExecution;
import com.sca4d.schedule.ScheduleResourceTypes;
import com.sca4d.schedule.ScheduleService;
import com.sca4d.schedule.request.ScheduleCreateRequest;
import com.sca4d.schedule.request.SchedulePageRequest;
import com.sca4d.schedule.request.ScheduleUpdateRequest;
import com.sca4d.schedule.view.ScheduleExecutionView;
import com.sca4d.schedule.view.ScheduleTaskTypeView;
import com.sca4d.schedule.view.ScheduleView;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PLATFORM Principal 管理 Schedule 主资源、执行子资源和任务类型目录的 HTTP 接口。 */
@RestController
@RequestMapping("/mg/platform")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
public class PlatformScheduleController {

    private final ScheduleService service;
    private final ObjectMapper objectMapper;
    private final LocalizedMessages messages;

    public PlatformScheduleController(
            @NotNull ScheduleService service,
            @NotNull ObjectMapper objectMapper,
            @NotNull LocalizedMessages messages) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    /** @param request 分页和筛选条件 @return Schedule 完整资源表示分页 */
    @GetMapping("/schedules")
    @RequirePermission(PlatformPermissions.SCHEDULES_READ)
    public @NotNull ApiResponse<PageData<ScheduleView>> page(@Valid @NotNull SchedulePageRequest request) {
        return ApiResponse.ok(service
                .page(
                        request.getKeyword(),
                        request.getScheduleType(),
                        request.getStatus(),
                        request.getTaskType(),
                        request.page(),
                        request.size())
                .map(schedule -> ScheduleView.from(schedule, objectMapper)));
    }

    /** @param scheduleId Schedule 主键 @return 带当前 ETag 的完整资源表示 */
    @GetMapping("/schedules/{scheduleId}")
    @RequirePermission(PlatformPermissions.SCHEDULES_READ)
    public @NotNull ApiResponse<ScheduleView> detail(@PathVariable @NotNull Long scheduleId) {
        Schedule schedule = service.require(scheduleId);
        return ApiResponse.ok(ScheduleView.from(schedule, objectMapper), schedule.getVersion());
    }

    /**
     * @param request 完整创建表示
     * @param idempotencyKey 客户端一次创建意图的请求头
     * @param principal 当前 PLATFORM Principal
     * @return 201、ETag 和完整新资源表示
     */
    @PostMapping("/schedules")
    @Idempotent(operation = "platform.schedules.create", key = "#idempotencyKey")
    @Audit(module = PlatformAuditOperations.SCHEDULES, action = PlatformAuditOperations.CREATE, targetType = ScheduleResourceTypes.SCHEDULE, targetId = "#result?.data?.scheduleId", payloadType = "platform.schedule", payload = "#result == null ? T(com.sca4d.schedule.audit.ScheduleAuditPayload).failure() : T(com.sca4d.schedule.audit.ScheduleAuditPayload).schedule("
            + "#result.data.taskType, #result.data.scheduleType, #result.data.executeAt, "
            + "#result.data.cronExpression, #result.data.timeZone, #result.data.status, "
            + "#result.data.taskPayload.path('recipients').size())")
    @RequirePermission(PlatformPermissions.SCHEDULES_MANAGE)
    public @NotNull ApiResponse<ScheduleView> create(
            @Valid @RequestBody @NotNull ScheduleCreateRequest request,
            @RequestHeader("Idempotency-Key") @NotNull String idempotencyKey,
            @AuthPrincipal @NotNull Principal principal) {
        Schedule schedule = service.create(
                request.getName(),
                request.getScheduleType(),
                request.getExecuteAt(),
                request.getCronExpression(),
                request.getTimeZone(),
                request.getTaskType(),
                request.getTaskPayload().toString(),
                principal.requirePrincipalId());
        return ApiResponse.created(ScheduleView.from(schedule, objectMapper), schedule.getVersion());
    }

    /**
     * @param scheduleId Schedule 主键
     * @param request 完整可修改表示
     * @param ifMatch 当前资源强 ETag
     * @return 200、下一版本 ETag 和完整资源表示
     */
    @PutMapping("/schedules/{scheduleId}")
    @Audit(module = PlatformAuditOperations.SCHEDULES, action = PlatformAuditOperations.UPDATE, targetType = ScheduleResourceTypes.SCHEDULE, targetId = "#scheduleId", payloadType = "platform.schedule", payload = "#result == null ? T(com.sca4d.schedule.audit.ScheduleAuditPayload).failure() : T(com.sca4d.schedule.audit.ScheduleAuditPayload).schedule("
            + "#result.data.taskType, #result.data.scheduleType, #result.data.executeAt, "
            + "#result.data.cronExpression, #result.data.timeZone, #result.data.status, "
            + "#result.data.taskPayload.path('recipients').size())")
    @RequirePermission(PlatformPermissions.SCHEDULES_MANAGE)
    public @NotNull ApiResponse<ScheduleView> update(
            @PathVariable @NotNull Long scheduleId,
            @Valid @RequestBody @NotNull ScheduleUpdateRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        long expectedVersion = IfMatchVersion.require(
                ifMatch,
                ScheduleErrorCode.SCHEDULE_IF_MATCH_REQUIRED,
                ScheduleErrorCode.SCHEDULE_VERSION_CHANGED);
        Schedule schedule = service.update(
                scheduleId,
                request.getName(),
                request.getScheduleType(),
                request.getExecuteAt(),
                request.getCronExpression(),
                request.getTimeZone(),
                request.getStatus(),
                request.getTaskType(),
                request.getTaskPayload().toString(),
                expectedVersion);
        return ApiResponse.ok(ScheduleView.from(schedule, objectMapper), schedule.getVersion());
    }

    /** @param scheduleId Schedule 主键 @param ifMatch 当前资源强 ETag @return 204 无响应体 */
    @DeleteMapping("/schedules/{scheduleId}")
    @Audit(module = PlatformAuditOperations.SCHEDULES, action = PlatformAuditOperations.DELETE, targetType = ScheduleResourceTypes.SCHEDULE, targetId = "#scheduleId", payloadType = "platform.schedule", payload = "T(com.sca4d.schedule.audit.ScheduleAuditPayload).deleted()")
    @RequirePermission(PlatformPermissions.SCHEDULES_MANAGE)
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long scheduleId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        service.delete(scheduleId, IfMatchVersion.require(
                ifMatch,
                ScheduleErrorCode.SCHEDULE_IF_MATCH_REQUIRED,
                ScheduleErrorCode.SCHEDULE_VERSION_CHANGED));
        return ResponseEntity.noContent().build();
    }

    /** @param scheduleId Schedule 主键 @param request 分页参数 @return 执行历史分页 */
    @GetMapping("/schedules/{scheduleId}/executions")
    @RequirePermission(PlatformPermissions.SCHEDULES_READ)
    public @NotNull ApiResponse<PageData<ScheduleExecutionView>> executions(
            @PathVariable @NotNull Long scheduleId, @NotNull PageQuery request) {
        return ApiResponse.ok(service
                .pageExecutions(scheduleId, request.page(), request.size())
                .map(execution -> ScheduleExecutionView.from(execution, messages)));
    }

    /** @param scheduleId Schedule 主键 @param executionId 执行主键 @return 单次执行资源 */
    @GetMapping("/schedules/{scheduleId}/executions/{executionId}")
    @RequirePermission(PlatformPermissions.SCHEDULES_READ)
    public @NotNull ApiResponse<ScheduleExecutionView> execution(
            @PathVariable @NotNull Long scheduleId, @PathVariable @NotNull Long executionId) {
        return ApiResponse.ok(ScheduleExecutionView.from(service.requireExecution(scheduleId, executionId), messages));
    }

    /**
     * @param scheduleId Schedule 主键
     * @param idempotencyKey 客户端一次人工执行意图的请求头
     * @return 202 和 PENDING execution 资源
     */
    @PostMapping("/schedules/{scheduleId}/executions")
    @Idempotent(operation = "platform.schedules.executions.create", key = "#idempotencyKey")
    @Audit(module = PlatformAuditOperations.SCHEDULES, action = PlatformAuditOperations.CREATE_EXECUTION, targetType = ScheduleResourceTypes.SCHEDULE_EXECUTION, targetId = "#result?.data?.executionId", payloadType = "platform.schedule", payload = "#result == null ? T(com.sca4d.schedule.audit.ScheduleAuditPayload).failure() : T(com.sca4d.schedule.audit.ScheduleAuditPayload).execution("
            + "#result.data.taskType, #result.data.triggerSource)")
    @RequirePermission(PlatformPermissions.SCHEDULES_MANAGE)
    public @NotNull ApiResponse<ScheduleExecutionView> createExecution(
            @PathVariable @NotNull Long scheduleId,
            @RequestHeader("Idempotency-Key") @NotNull String idempotencyKey) {
        ScheduleExecution execution = service.createManualExecution(scheduleId);
        return ApiResponse.accepted(ScheduleExecutionView.from(execution, messages));
    }

    /** @return 当前应用可创建的 ScheduleTask 类型目录 */
    @GetMapping("/schedule-task-types")
    @RequirePermission(PlatformPermissions.SCHEDULES_READ)
    public @NotNull ApiResponse<List<ScheduleTaskTypeView>> taskTypes() {
        return ApiResponse.ok(service.taskTypes().stream()
                .map(taskType -> ScheduleTaskTypeView.from(taskType, messages))
                .toList());
    }

}
